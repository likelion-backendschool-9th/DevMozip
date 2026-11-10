package com.devmozip.backend.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** WBS 14.1 관리자 대시보드 (docs/admin-plan.md 9단계). 수치 기준(5.2)과 최근 운영 변경(D7)을 본다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AdminDashboardIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;

	static final String THIS_MONTH = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.sql.init.mode", () -> "never");
	}

	@BeforeEach
	void fixtures() {
		for (String table : List.of("sanctions", "reports", "payments", "subscriptions", "subscription_plans", "ai_usages", "comments", "posts", "projects", "users")) {
			jdbc.update("DELETE FROM " + table);
		}
		user(1, "운영자", "admin", "ADMIN", "ACTIVE");
		user(2, "김개발", "kim", "PREMIUM", "ACTIVE");
		user(3, "이정지", "lee", "USER", "SUSPENDED");
		user(4, "박경고", "park", "USER", "WARNED");
		jdbc.update("INSERT INTO users(id, nickname, is_private) VALUES (5, '탈퇴회원', false)");
		jdbc.update("INSERT INTO subscription_plans(id, name, price, currency, billing_cycle, is_active) VALUES (1, '프리미엄 월간', 9900, 'KRW', 'MONTHLY', true)");
		jdbc.update("INSERT INTO subscriptions(id, user_id, plan_id, status, starts_at, current_period_end) VALUES (1, 2, 1, 'CANCEL_REQUESTED', localtimestamp, localtimestamp + INTERVAL '10 days')");
		jdbc.update("INSERT INTO subscriptions(id, user_id, plan_id, status, starts_at, current_period_end, ended_at) VALUES (2, 5, 1, 'CANCELED', localtimestamp, localtimestamp, localtimestamp)");
		project(1, null);
		project(2, java.sql.Timestamp.valueOf("2026-11-10 10:00:00"));
		post(1, "VISIBLE", null);
		post(2, "RESTRICTED", null);
		post(3, "VISIBLE", java.sql.Timestamp.valueOf("2026-11-10 10:00:00"));
		report(1, "AWATING");
		report(2, "REVIEWING");
		report(3, "RESOLVED");
		jdbc.update("INSERT INTO ai_usages(user_id, usage_month, used_count, pending_count, created_at, updated_at) VALUES (2, ?, 3, 0, localtimestamp, localtimestamp)", THIS_MONTH);
		jdbc.update("INSERT INTO ai_usages(user_id, usage_month, used_count, pending_count, created_at, updated_at) VALUES (4, ?, 2, 1, localtimestamp, localtimestamp)", THIS_MONTH);
		jdbc.update("INSERT INTO ai_usages(user_id, usage_month, used_count, pending_count, created_at, updated_at) VALUES (4, '2000-01', 9, 0, localtimestamp, localtimestamp)");
	}

	void user(long id, String nickname, String tag, String role, String status) {
		jdbc.update("INSERT INTO users(id, email, nickname, tag, role, status, is_private) VALUES (?, ?, ?, ?, ?, ?, false)",
			id, "dash" + id + "@test.local", nickname, tag, role, status);
	}

	void project(long id, java.sql.Timestamp deleted) {
		jdbc.update("""
			INSERT INTO projects(id, host_member_id, title, description, recruitment_slots, tech_stacks, interests, image_url, is_public, is_restricted,
			status, view_count, recruitment_start, recruitment_end, development_start, development_end, deleted_at, created_at, updated_at)
			VALUES (?, 2, '공고', '설명', '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, 'https://example.test/a.png', true, false, 'RECRUITING', 0,
			DATE '2026-11-01', DATE '2026-11-30', DATE '2026-11-01', DATE '2027-01-31', ?, localtimestamp, localtimestamp)""", id, deleted);
	}

	void post(long id, String visibility, java.sql.Timestamp deleted) {
		jdbc.update("""
			INSERT INTO posts(id, author_id, type, category, title, content, tech_stacks, interests, image_urls, view_count, visibility, deleted_at, created_at, updated_at)
			VALUES (?, 2, 'POST', 'INFO', '글', '본문', '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, 0, ?, ?, localtimestamp, localtimestamp)""", id, visibility, deleted);
	}

	void report(long id, String status) {
		jdbc.update("INSERT INTO reports(id, reporter_id, reported_user_id, reason, status, created_at, updated_at) VALUES (?, 2, 4, '기타', ?, localtimestamp, localtimestamp)",
			id, status);
	}

	MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
		return request.header("Authorization", "Bearer " + jwt.createAccessToken("dash1@test.local", "ADMIN"));
	}

	@Test
	void countsFollowDashboardRules() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/admin/dashboard"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.members").value(4))
			.andExpect(jsonPath("$.premiumMembers").value(1))
			.andExpect(jsonPath("$.suspendedMembers").value(1))
			.andExpect(jsonPath("$.projects").value(1))
			.andExpect(jsonPath("$.posts").value(1))
			.andExpect(jsonPath("$.pendingReports").value(2))
			.andExpect(jsonPath("$.aiRecommendationsThisMonth").value(5))
			.andExpect(jsonPath("$.recentActions.length()").value(0));
		mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + jwt.createAccessToken("dash2@test.local", "PREMIUM")))
			.andExpect(status().isForbidden());
	}

	@Test
	void recentActionsMergeSanctionsAndReportHandlingNewestFirst() throws Exception {
		mvc.perform(asAdmin(patch("/api/v1/admin/users/park/ban").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\",\"reason\":\"반복 위반\"}")))
			.andExpect(status().isOk());
		mvc.perform(asAdmin(patch("/api/v1/admin/reports/1").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\",\"resolution\":\"정지 처리\"}")))
			.andExpect(status().isOk());
		mvc.perform(asAdmin(get("/api/v1/admin/dashboard"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.suspendedMembers").value(2))
			.andExpect(jsonPath("$.pendingReports").value(1))
			.andExpect(jsonPath("$.recentActions.length()").value(2))
			.andExpect(jsonPath("$.recentActions[0].type").value("REPORT"))
			.andExpect(jsonPath("$.recentActions[0].summary").value("신고 처리 완료 · 사용자 신고 #1"))
			.andExpect(jsonPath("$.recentActions[0].actorNickname").value("운영자"))
			.andExpect(jsonPath("$.recentActions[1].type").value("SANCTION"))
			.andExpect(jsonPath("$.recentActions[1].summary").value("회원 정지 · 박경고"));
		// 해제는 철회로 나온다. 철회한 관리자는 ERD에 칼럼이 없어 비어 있다. 정지한 회원이 다른 테스트에 남지 않게 하는 정리도 겸한다.
		mvc.perform(asAdmin(patch("/api/v1/admin/users/park/ban").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}")))
			.andExpect(status().isOk());
		mvc.perform(asAdmin(get("/api/v1/admin/dashboard")))
			.andExpect(jsonPath("$.recentActions.length()").value(3))
			.andExpect(jsonPath("$.recentActions[0].summary").value("회원 정지 철회 · 박경고"))
			.andExpect(jsonPath("$.recentActions[0].actorNickname").value(org.hamcrest.Matchers.nullValue()));
	}
}
