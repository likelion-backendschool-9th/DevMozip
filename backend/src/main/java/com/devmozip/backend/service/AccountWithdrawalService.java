package com.devmozip.backend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.devmozip.backend.client.KakaoClient;
import com.devmozip.backend.dto.member.AccountWithdrawalRequest;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.AccountWithdrawalRepository;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.security.payment.BillingKeyCipher;
import com.devmozip.backend.security.payment.PaymentMethodCodec;
import com.devmozip.backend.storage.LocalProfileImageStorage;
import com.devmozip.backend.repository.ProjectRepository;
import com.devmozip.backend.repository.ProjectMemberRepository;
import com.devmozip.backend.entity.ProjectMember;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.event.ProjectLifecycleChanged;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/** 인증된 본인 계정만 탈퇴시키고 외부 정리 작업을 커밋 후 재시도한다. */
@Service
public class AccountWithdrawalService {
	private static final Logger log = LoggerFactory.getLogger(AccountWithdrawalService.class);
	private final MemberRepository members;
	private final AccountWithdrawalRepository accounts;
	private final PasswordEncoder passwords;
	private final KakaoClient kakao;
	private final PaymentMethodCodec paymentMethods;
	private final BillingKeyCipher cipher;
	private final RefreshTokenStore refreshTokens;
	private final PasswordResetService resetTokens;
	private final LocalProfileImageStorage images;
	private final AccountWithdrawalTasks tasks;
	private final AccountWithdrawalCleanup cleanup;
	private final Clock clock;
	private final TransactionTemplate transaction;
	private final ProjectRepository projects;
	private final ProjectMemberRepository participants;
	private final ApplicationEventPublisher events;

	public AccountWithdrawalService(MemberRepository members, AccountWithdrawalRepository accounts, PasswordEncoder passwords,
		KakaoClient kakao, PaymentMethodCodec paymentMethods, BillingKeyCipher cipher, RefreshTokenStore refreshTokens,
		PasswordResetService resetTokens, LocalProfileImageStorage images, AccountWithdrawalTasks tasks,
		AccountWithdrawalCleanup cleanup, @Qualifier("paymentClock") Clock clock, PlatformTransactionManager manager,
		ProjectRepository projects, ProjectMemberRepository participants, ApplicationEventPublisher events) {
		this.members = members; this.accounts = accounts; this.passwords = passwords; this.kakao = kakao;
		this.paymentMethods = paymentMethods; this.cipher = cipher; this.refreshTokens = refreshTokens;
		this.resetTokens = resetTokens; this.images = images; this.tasks = tasks; this.cleanup = cleanup;
		this.clock = clock; this.transaction = new TransactionTemplate(manager);
		this.projects = projects; this.participants = participants; this.events = events;
	}

	public void withdraw(String email, AccountWithdrawalRequest request) {
		if (!StringUtils.hasText(email)) throw error(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		if (request == null || !request.confirmed()) throw error(HttpStatus.BAD_REQUEST, "탈퇴 시 개인정보 삭제와 환불 불가 안내를 확인해 주세요.");
		Long userId = transaction.execute(status -> {
			Member member = members.findForAccountUpdateByEmail(email)
				.orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
			if (member.getStatus() != Member.MemberStatus.ACTIVE && member.getStatus() != Member.MemberStatus.WARNED)
				throw error(HttpStatus.FORBIDDEN, "이 계정 상태에서는 탈퇴할 수 없습니다.");
			verifyPassword(member, request.currentPassword());
			requireNoHostedProject(member.getId());
			if (accounts.lockSubscriptions(member.getId()))
				throw error(HttpStatus.CONFLICT, "결제 처리 중에는 탈퇴할 수 없습니다. 결제 결과 확인 후 다시 시도해 주세요.");
			String kakaoToken = verifyKakao(member, request.kakaoCode());
			String encryptedBillingKey = paymentMethods.read(member).map(method -> cipher.encrypt(method.billingKey())).orElse(null);
			if (StringUtils.hasText(member.getPaymentMethods()) && encryptedBillingKey == null)
				throw error(HttpStatus.SERVICE_UNAVAILABLE, "등록된 결제 수단을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.");
			var files = new ArrayList<String>();
			addImage(files, member.getProfileImage()); addImage(files, member.getBannerImage());
			// DB를 비우기 전에 영속 작업을 남겨 서버가 종료되어도 빌링키와 파일을 다시 정리할 수 있게 한다.
			var job = new AccountWithdrawalTasks.Job(member.getId(), encryptedBillingKey, List.copyOf(files));
			tasks.save(job);
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override public void afterCompletion(int completion) {
					if (completion == STATUS_ROLLED_BACK) {
						try { tasks.removePrepared(job); }
						catch (RuntimeException e) { log.warn("롤백된 탈퇴 준비 작업 정리를 확인해야 합니다: userId={}", job.userId()); }
					}
				}
			});
			tasks.revokeLegacy(email);
			refreshTokens.remove(email);
			resetTokens.removeToken(email);
			if (kakaoToken != null) unlinkKakao(kakaoToken);
			LocalDateTime now = LocalDateTime.now(clock);
			leaveProjects(member.getId(), now);
			accounts.erasePersonalData(member.getId(), now);
			return member.getId();
		});
		if (userId != null) {
			try { cleanup.process("withdrawal:cleanup:" + userId); }
			catch (RuntimeException e) { log.warn("탈퇴 후 정리를 다음 주기에 재시도합니다: userId={}", userId); }
		}
	}

	private void leaveProjects(long userId, LocalDateTime now) {
		for (long id : accounts.participatingProjects(userId)) {
			var project = projects.findLiveForUpdate(id).orElse(null);
			if (project == null || project.getStatus() == ProjectStatus.COMPLETED) continue;
			participants.findByProjectIdAndUserIdAndStatus(id, userId, ProjectMember.Status.ACTIVE).ifPresent(member -> member.leave(now));
			participants.flush();
			accounts.cancelApplications(id, userId, now);
			long active = participants.countByProjectIdAndStatus(id, ProjectMember.Status.ACTIVE);
			long requested = participants.countByProjectIdAndStatusAndEndRequestedAtIsNotNull(id, ProjectMember.Status.ACTIVE);
			if (project.getStatus() == ProjectStatus.IN_PROGRESS && active > 0 && requested > active / 2) {
				project.changeStatus(ProjectStatus.COMPLETED, now);
				events.publishEvent(new ProjectLifecycleChanged(ProjectLifecycleChanged.Type.COMPLETED, id, userId,
					ProjectStatus.IN_PROGRESS, ProjectStatus.COMPLETED, project.getRecruitmentEnd(), project.getRecruitmentEnd(),
					project.getDevelopmentEnd(), project.getDevelopmentEnd(), now));
			}
		}
	}

	private void verifyPassword(Member member, String supplied) {
		if (member.getPasswordHash() == null) {
			if (member.getKakaoId() == null) throw error(HttpStatus.CONFLICT, "본인 확인 수단이 없는 계정입니다.");
			return;
		}
		if (!StringUtils.hasText(supplied) || supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
			|| !passwords.matches(supplied, member.getPasswordHash()))
			throw error(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다.");
	}

	private void requireNoHostedProject(long userId) {
		if (!accounts.unfinishedHosted(userId).isEmpty())
			throw error(HttpStatus.CONFLICT, "주최 중인 프로젝트가 있어 탈퇴할 수 없습니다. 해당 프로젝트를 종료하거나 삭제한 후 다시 시도해 주세요.");
	}

	private String verifyKakao(Member member, String code) {
		if (member.getKakaoId() == null) return null;
		if (!StringUtils.hasText(code)) throw error(HttpStatus.BAD_REQUEST, "연동한 카카오 계정으로 다시 인증해 주세요.");
		String token = kakao.requestAccessToken(code);
		if (!member.getKakaoId().equals(kakao.fetchKakaoId(token)))
			throw error(HttpStatus.CONFLICT, "연동한 카카오 계정으로 인증해 주세요.");
		return token;
	}

	private void unlinkKakao(String token) {
		for (int attempt = 0; attempt < 3; attempt++) {
			try { kakao.unlink(token); return; }
			catch (ResponseStatusException e) {
				if (!e.getStatusCode().is5xxServerError()) throw e;
				if (attempt == 2) throw error(HttpStatus.SERVICE_UNAVAILABLE, "카카오 연결 해제를 완료하지 못했습니다. 다시 인증한 뒤 탈퇴를 시도해 주세요.");
			}
		}
	}

	private void addImage(List<String> files, String url) {
		String name = images.ownedFilename(url);
		if (name != null && !files.contains(name)) files.add(name);
	}

	private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
