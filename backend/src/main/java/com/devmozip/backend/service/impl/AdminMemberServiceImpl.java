package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.devmozip.backend.dto.admin.AdminBanRequest;
import com.devmozip.backend.dto.admin.AdminMemberResponse;
import com.devmozip.backend.dto.admin.AdminPageResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberRole;
import com.devmozip.backend.entity.Member.MemberStatus;
import com.devmozip.backend.entity.Report;
import com.devmozip.backend.entity.Sanction;
import com.devmozip.backend.repository.AdminMemberQueryRepository;
import com.devmozip.backend.repository.ReportRepository;
import com.devmozip.backend.repository.SanctionRepository;
import com.devmozip.backend.security.admin.AdminAccess;
import com.devmozip.backend.security.jwt.SuspendedMemberStore;
import com.devmozip.backend.service.AdminMemberService;
import com.devmozip.backend.service.AdminPaging;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관리자 회원 조회·제재 (docs/admin-plan.md 4.3, 5.3, 5.4).
 * 제재는 대상 회원 행을 잠그고 상태를 바꾼 뒤 sanctions에 기록한다. 정지 목록은 커밋이 끝난 뒤에만 바꾼다.
 */
@Service
public class AdminMemberServiceImpl implements AdminMemberService {
	static final int MAX_REASON_LENGTH = 300;

	private final AdminAccess access;
	private final AdminMemberQueryRepository members;
	private final SanctionRepository sanctions;
	private final ReportRepository reports;
	private final SuspendedMemberStore suspendedMembers;
	private final Clock clock;

	public AdminMemberServiceImpl(AdminAccess access, AdminMemberQueryRepository members, SanctionRepository sanctions,
		ReportRepository reports, SuspendedMemberStore suspendedMembers, @Qualifier("adminClock") Clock clock) {
		this.access = access;
		this.members = members;
		this.sanctions = sanctions;
		this.reports = reports;
		this.suspendedMembers = suspendedMembers;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public AdminPageResponse<AdminMemberResponse> list(String q, String status, String role, int page, int size) {
		access.require();
		MemberStatus statusFilter = AdminPaging.option(MemberStatus.class, status, "회원 상태를 확인해 주세요.");
		MemberRole roleFilter = AdminPaging.option(MemberRole.class, role, "회원 역할을 확인해 주세요.");
		Page<Member> rows = members.search(AdminPaging.keyword(q), statusFilter, roleFilter, AdminPaging.request(page, size));
		Map<Long, Sanction> latest = rows.isEmpty() ? Map.of()
			: sanctions.findLatestByUserIds(rows.map(Member::getId).getContent()).stream()
				.collect(Collectors.toMap(s -> s.getUser().getId(), Function.identity()));
		return AdminPageResponse.of(rows, member -> AdminMemberResponse.of(member, latest.get(member.getId())));
	}

	@Override
	@Transactional
	public AdminMemberResponse ban(String slug, AdminBanRequest request) {
		Member admin = access.require();
		MemberStatus next = request.status();
		String reason = request.reason() == null ? "" : request.reason().strip();
		if (next != MemberStatus.ACTIVE && reason.isEmpty()) {
			throw error(HttpStatus.BAD_REQUEST, "경고·정지 사유를 입력해 주세요.");
		}
		// 해제는 유효한 제재의 revoked_at만 채운다. ERD에 해제 사유·근거 칼럼이 없어 받지 않는다.
		if (next == MemberStatus.ACTIVE && (!reason.isEmpty() || request.reportId() != null)) {
			throw error(HttpStatus.BAD_REQUEST, "해제에는 사유와 근거 신고를 함께 보내지 않습니다.");
		}
		if (reason.length() > MAX_REASON_LENGTH) {
			throw error(HttpStatus.BAD_REQUEST, "사유는 300자 이하로 입력해 주세요.");
		}
		Member target = members.findForSanctionByTag(slug)
			.orElseThrow(() -> error(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
		if (target.getId().equals(admin.getId())) {
			throw error(HttpStatus.FORBIDDEN, "본인 계정은 제재할 수 없습니다.");
		}
		if (target.getRole() == MemberRole.ADMIN) {
			throw error(HttpStatus.FORBIDDEN, "관리자 계정은 제재할 수 없습니다.");
		}
		MemberStatus previous = target.getStatus();
		if (previous == next) {
			throw error(HttpStatus.CONFLICT, "이미 같은 상태인 회원입니다.");
		}
		Report report = request.reportId() == null ? null : reports.findById(request.reportId())
			.orElseThrow(() -> error(HttpStatus.NOT_FOUND, "근거 신고를 찾을 수 없습니다."));

		LocalDateTime now = LocalDateTime.now(clock);
		// 유효한 제재를 철회한다(ERD revoked_at). 해제면 여기서 끝나고, 경고·정지면 새 제재 행을 만든다.
		List<Sanction> active = sanctions.findByUser_IdAndRevokedAtIsNull(target.getId());
		active.forEach(sanction -> sanction.revoke(now));
		target.changeStatus(next);
		Sanction latest = next == MemberStatus.ACTIVE
			? active.stream().max(Comparator.comparing(Sanction::getId))
				.orElseGet(() -> sanctions.findLatestByUserIds(List.of(target.getId())).stream().findFirst().orElse(null))
			: sanctions.save(Sanction.start(target, admin, report, typeOf(next), reason, now));
		afterCommit(target.getEmail(), previous, next);
		return AdminMemberResponse.of(target, latest);
	}

	/** 정지 목록은 서버 메모리라 트랜잭션과 함께 되돌릴 수 없다. 커밋이 끝난 뒤에만 바꾼다. */
	private void afterCommit(String email, MemberStatus previous, MemberStatus next) {
		if (email == null || (next != MemberStatus.SUSPENDED && previous != MemberStatus.SUSPENDED)) {
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				if (next == MemberStatus.SUSPENDED) {
					suspendedMembers.suspend(email);
				} else {
					suspendedMembers.release(email);
				}
			}
		});
	}

	private static Sanction.Type typeOf(MemberStatus status) {
		return status == MemberStatus.SUSPENDED ? Sanction.Type.SUSPENDED : Sanction.Type.WARNED;
	}

	private static ResponseStatusException error(HttpStatus status, String message) {
		return new ResponseStatusException(status, message);
	}
}
