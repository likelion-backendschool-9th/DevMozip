package com.devmozip.backend.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.devmozip.backend.client.KakaoClient;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.entity.ProjectMember;
import com.devmozip.backend.entity.ProjectRecruitmentSlot;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.ProjectRepository;
import com.devmozip.backend.repository.ProjectMemberRepository;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.service.AccountWithdrawalCleanup;
import com.devmozip.backend.service.AccountWithdrawalTasks;
import com.devmozip.backend.service.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AccountWithdrawalIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@Autowired MockMvc mvc;
	@Autowired MemberRepository members;
	@Autowired ProjectRepository projects;
	@Autowired ProjectMemberRepository participants;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;
	@Autowired PasswordEncoder passwords;
	@Autowired AccountWithdrawalTasks tasks;
	@Autowired com.devmozip.backend.repository.ProjectApplicationRepository applications;
	@Autowired com.devmozip.backend.repository.ProjectApplicationPortfolioRepository portfolios;
	@Autowired com.devmozip.backend.repository.ProjectApplicationPortfolioSnapshotRepository snapshots;
	@MockitoBean RefreshTokenStore refreshTokens;
	@MockitoBean PasswordResetService resetTokens;
	@MockitoBean KakaoClient kakao;
	@MockitoBean AccountWithdrawalCleanup cleanup;

	private Member member(boolean kakaoOnly) {
		String key = UUID.randomUUID().toString();
		return members.saveAndFlush(Member.builder().email(key + "@withdraw.test").nickname("회원" + key)
			.passwordHash(kakaoOnly ? null : passwords.encode("Password123"))
			.kakaoId(kakaoOnly ? key : null).tag("tag-" + key).summary("개인 소개")
			.profileImage("https://example.test/photo").externalLinks("{\"github\":\"https://github.com/example\"}")
			.role(Member.MemberRole.PREMIUM).status(Member.MemberStatus.ACTIVE).build());
	}

	private String token(Member member) { return jwt.createAccessToken(member.getEmail(), "PREMIUM"); }
	private void withdraw(Member member, String body, int status) throws Exception {
		mvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + token(member))
			.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(status));
	}

	@Test void keepsOnlyIdAndNicknameAndRejectsOldTokenAfterRejoining() throws Exception {
		Member member = member(false);
		String oldToken = token(member);
		String oldRefresh = jwt.createRefreshToken(member.getEmail());
		withdraw(member, "{\"currentPassword\":\"Password123\",\"confirmed\":true}", 204);
		var row = jdbc.queryForMap("SELECT * FROM users WHERE id=?", member.getId());
		assertThat(row.get("nickname")).isEqualTo(member.getNickname());
		row.remove("id"); row.remove("nickname");
		assertThat(row.values()).allMatch(value -> value == null);
		verify(refreshTokens).remove(member.getEmail());
		verify(resetTokens).removeToken(member.getEmail());
		Member rejoined = members.saveAndFlush(Member.builder().email(member.getEmail()).nickname("새회원" + UUID.randomUUID())
			.passwordHash(passwords.encode("Password123")).role(Member.MemberRole.USER).status(Member.MemberStatus.ACTIVE).build());
		assertThat(rejoined.getId()).isNotEqualTo(member.getId());
		assertThat(jwt.validateRefreshToken(oldRefresh)).isFalse();
		mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + oldToken)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token(rejoined))).andExpect(status().isOk());
	}

	@Test void rejectsWrongPasswordAndMissingConfirmation() throws Exception {
		Member member = member(false);
		withdraw(member, "{\"currentPassword\":\"wrong\",\"confirmed\":true}", 400);
		withdraw(member, "{\"currentPassword\":\"Password123\",\"confirmed\":false}", 400);
		assertThat(members.findByEmail(member.getEmail())).isPresent();
		verifyNoInteractions(cleanup);
	}

	private Project hosted(Member member) {
		LocalDate day = LocalDate.now();
		Project project = projects.saveAndFlush(new Project(member.getId(), "탈퇴 정책 확인", "요약", "프로젝트 설명", "목표", "https://example.test/project.png", true,
			List.of(new ProjectRecruitmentSlot("백엔드 개발자", 2, 0)), List.of("Java"), List.of("AI"), day, day.plusDays(3), day.plusDays(4), day.plusDays(10), LocalDateTime.now()));
		participants.saveAndFlush(ProjectMember.host(project, member.getId(), LocalDateTime.now()));
		return project;
	}

	@Test void blocksUnfinishedHostAndAllowsDeletedProject() throws Exception {
		Member member = member(false);
		Project project = hosted(member);
		withdraw(member, "{\"currentPassword\":\"Password123\",\"confirmed\":true}", 409);
		assertThat(members.findByEmail(member.getEmail())).isPresent();
		jdbc.update("UPDATE projects SET deleted_at=? WHERE id=?", LocalDateTime.now(), project.getId());
		withdraw(member, "{\"currentPassword\":\"Password123\",\"confirmed\":true}", 204);
		assertThat(projects.findById(project.getId())).isPresent();
		assertThat(participants.findByProjectId(project.getId())).hasSize(1);
	}

	@Test void preservesCompletedHostRecordAndNickname() throws Exception {
		Member member = member(false);
		Project project = hosted(member);
		jdbc.update("UPDATE projects SET status='COMPLETED',completed_at=? WHERE id=?", LocalDateTime.now(), project.getId());
		withdraw(member, "{\"currentPassword\":\"Password123\",\"confirmed\":true}", 204);
		mvc.perform(get("/api/v1/projects/" + project.getId())).andExpect(status().isOk())
			.andExpect(jsonPath("host.nickname").value(member.getNickname())).andExpect(jsonPath("host.tag").isEmpty());
	}

	@Test void kakaoOnlyRequiresMatchingReauthentication() throws Exception {
		Member member = member(true);
		withdraw(member, "{\"confirmed\":true}", 400);
		when(kakao.requestAccessToken("code")).thenReturn("provider-token");
		when(kakao.fetchKakaoId("provider-token")).thenReturn("other-account");
		withdraw(member, "{\"kakaoCode\":\"code\",\"confirmed\":true}", 409);
		verify(kakao, never()).unlink(anyString());
		when(kakao.fetchKakaoId("provider-token")).thenReturn(member.getKakaoId());
		withdraw(member, "{\"kakaoCode\":\"code\",\"confirmed\":true}", 204);
		verify(kakao).unlink("provider-token");
	}

	@Test void requiresLogin() throws Exception {
		mvc.perform(delete("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirmed\":true}")).andExpect(status().isUnauthorized());
	}

	@Test void stopsSubscriptionWithoutRefundAndKeepsTransaction() throws Exception {
		Member member = member(false);
		Long plan = jdbc.queryForObject("SELECT id FROM subscription_plans ORDER BY id LIMIT 1", Long.class);
		Long subscription = jdbc.queryForObject("INSERT INTO subscriptions(user_id,plan_id,status,starts_at,current_period_end) VALUES (?,?,'ACTIVE',?,?) RETURNING id",
			Long.class, member.getId(), plan, LocalDateTime.now(), LocalDateTime.now().plusMonths(1));
		jdbc.update("INSERT INTO payments(subscription_id,order_id,provider,amount,currency,status,paid_at,created_at) VALUES (?,?,'TOSS_PAYMENTS',9900,'KRW','PAID',?,?)",
			subscription, UUID.randomUUID().toString(), LocalDateTime.now(), LocalDateTime.now());
		withdraw(member, "{\"currentPassword\":\"Password123\",\"confirmed\":true}", 204);
		assertThat(jdbc.queryForObject("SELECT status FROM subscriptions WHERE id=?", String.class, subscription)).isEqualTo("CANCELED");
		assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE subscription_id=?", String.class, subscription)).isEqualTo("PAID");
	}

	@Test void blocksRenewalInProgress() throws Exception {
		Member member = member(false);
		Long plan = jdbc.queryForObject("SELECT id FROM subscription_plans ORDER BY id LIMIT 1", Long.class);
		jdbc.update("INSERT INTO subscriptions(user_id,plan_id,status,starts_at,current_period_end) VALUES (?,?,'RENEWING',?,?)", member.getId(), plan, LocalDateTime.now(), LocalDateTime.now().plusMonths(1));
		withdraw(member, "{\"currentPassword\":\"Password123\",\"confirmed\":true}", 409);
		assertThat(members.findByEmail(member.getEmail())).isPresent();
	}

	@Test void leavesActiveProjectAndScrubsSnapshotAuthorWhilePreservingBody() throws Exception {
		Member host = member(false), leaving = member(false);
		Project project = hosted(host);
		var now = LocalDateTime.now();
		participants.saveAndFlush(ProjectMember.participant(project, leaving.getId(), "백엔드 개발자", now));
		var application = applications.saveAndFlush(new com.devmozip.backend.entity.ProjectApplication(project, leaving.getId(), "백엔드 개발자", "보존할 지원 본문", now));
		var link = portfolios.saveAndFlush(new com.devmozip.backend.entity.ProjectApplicationPortfolio(application, 123456, now));
		var snapshot = snapshots.saveAndFlush(new com.devmozip.backend.entity.ProjectApplicationPortfolioSnapshot(link, 123456, "제출본", "{\"author\":{\"nickname\":\"기록 닉네임\",\"tag\":\"private\",\"profileImage\":\"private-photo\"},\"description\":\"보존할 내용\"}", now));
		jdbc.update("UPDATE projects SET status='IN_PROGRESS' WHERE id=?", project.getId());
		jdbc.update("UPDATE project_members SET end_requested_at=? WHERE project_id=? AND user_id=?", now, project.getId(), host.getId());
		withdraw(leaving, "{\"currentPassword\":\"Password123\",\"confirmed\":true}", 204);
		assertThat(jdbc.queryForObject("SELECT status FROM project_members WHERE project_id=? AND user_id=?", String.class, project.getId(), leaving.getId())).isEqualTo("LEFT");
		assertThat(jdbc.queryForObject("SELECT status FROM projects WHERE id=?", String.class, project.getId())).isEqualTo("COMPLETED");
		assertThat(jdbc.queryForObject("SELECT introduction FROM project_applications WHERE id=?", String.class, application.getId())).isEqualTo("보존할 지원 본문");
		String saved = jdbc.queryForObject("SELECT content::text FROM project_application_portfolio_snapshots WHERE id=?", String.class, snapshot.getId());
		assertThat(saved).contains("보존할 내용", "기록 닉네임").doesNotContain("private-photo", "\"private\"");
	}

	@Test void kakaoFailureRollsBackAndDiscardsPreparedCleanup() throws Exception {
		Member member = member(true);
		when(kakao.requestAccessToken("failed-code")).thenReturn("failed-token");
		when(kakao.fetchKakaoId("failed-token")).thenReturn(member.getKakaoId());
		doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)).when(kakao).unlink("failed-token");
		withdraw(member, "{\"kakaoCode\":\"failed-code\",\"confirmed\":true}", 503);
		assertThat(members.findByEmail(member.getEmail())).isPresent();
		assertThat(tasks.read("withdrawal:cleanup:" + member.getId())).isNull();
		verify(kakao, times(3)).unlink("failed-token");
	}
}
