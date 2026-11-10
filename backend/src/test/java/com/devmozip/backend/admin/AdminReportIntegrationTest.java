package com.devmozip.backend.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/**
 * WBS 14.8 신고 내역 처리 (docs/admin-plan.md 5단계, D5). 다섯 종류의 신고를 한 목록에서 거르고 처리한다.
 * 처리 규칙은 기존 프로젝트 신고 관리와 같고, 처리해도 제재·숨김·삭제는 일어나지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AdminReportIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;

	static final String REPORTS = "/api/v1/admin/reports";
	static final long ADMIN = 1, REPORTER = 2, TARGET = 3, WITHDRAWN = 4, PROJECT = 30, POST = 40, COMMENT = 50;
	static final long R_PROJECT = 101, R_MEMBER = 102, R_USER = 103, R_POST = 104, R_COMMENT = 105, R_WITHDRAWN = 106;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.sql.init.mode", () -> "never");
	}

	@BeforeEach
	void fixtures() {
		for (String table : List.of("sanctions", "reports", "comments", "posts", "projects", "users")) jdbc.update("DELETE FROM " + table);
		user(ADMIN, "운영자", "admin", "ADMIN");
		user(REPORTER, "신고자", "reporter", "USER");
		user(TARGET, "대상자", "target", "USER");
		jdbc.update("INSERT INTO users(id, nickname, is_private) VALUES (?, '탈퇴회원', false)", WITHDRAWN);
		jdbc.update("""
			INSERT INTO projects(id, host_member_id, title, description, recruitment_slots, tech_stacks, interests, image_url, is_public, is_restricted,
			status, view_count, recruitment_start, recruitment_end, development_start, development_end, created_at, updated_at)
			VALUES (?, ?, '신고된 공고', '설명', '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, 'https://example.test/a.png', true, false, 'RECRUITING', 0,
			DATE '2026-11-01', DATE '2026-11-30', DATE '2026-11-01', DATE '2027-01-31', localtimestamp, localtimestamp)""", PROJECT, TARGET);
		jdbc.update("""
			INSERT INTO posts(id, author_id, type, category, title, content, tech_stacks, interests, image_urls, view_count, visibility, created_at, updated_at)
			VALUES (?, ?, 'POST', 'INFO', '신고된 글', '본문', '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, 0, 'VISIBLE', localtimestamp, localtimestamp)""", POST, TARGET);
		jdbc.update("INSERT INTO comments(id, post_id, author_id, content, created_at, updated_at) VALUES (?, ?, ?, '신고된 댓글', localtimestamp, localtimestamp)",
			COMMENT, POST, TARGET);
		report(R_PROJECT, REPORTER, null, PROJECT, null, null, "허위 모집 정보");
		report(R_MEMBER, REPORTER, TARGET, PROJECT, null, null, "기타");
		report(R_USER, REPORTER, TARGET, null, null, null, "욕설");
		report(R_POST, REPORTER, null, null, POST, null, "스팸");
		report(R_COMMENT, REPORTER, null, null, null, COMMENT, "광고");
		report(R_WITHDRAWN, WITHDRAWN, TARGET, null, null, null, "기타");
	}

	void user(long id, String nickname, String tag, String role) {
		jdbc.update("INSERT INTO users(id, email, nickname, tag, role, status, is_private) VALUES (?, ?, ?, ?, ?, 'ACTIVE', false)",
			id, "admin-report" + id + "@test.local", nickname, tag, role);
	}

	void report(long id, long reporter, Long reported, Long project, Long post, Long comment, String reason) {
		jdbc.update("INSERT INTO reports(id, reporter_id, reported_user_id, project_id, post_id, comment_id, reason, status, created_at, updated_at)"
			+ " VALUES (?, ?, ?, ?, ?, ?, ?, 'AWATING', localtimestamp, localtimestamp)", id, reporter, reported, project, post, comment, reason);
	}

	MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
		return request.header("Authorization", "Bearer " + jwt.createAccessToken("admin-report1@test.local", "ADMIN"));
	}

	MockHttpServletRequestBuilder handle(long id, String body) {
		return asAdmin(patch(REPORTS + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	void listsAllKindsWithTargetsAndFilters() throws Exception {
		mvc.perform(asAdmin(get(REPORTS))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(6))
			.andExpect(jsonPath("$.items[0].id").value(R_WITHDRAWN))
			.andExpect(jsonPath("$.items[0].reporter.withdrawn").value(true))
			.andExpect(jsonPath("$.items[0].reporter.tag").doesNotExist())
			.andExpect(jsonPath("$.items[0].reporter.nickname").value("탈퇴회원"));
		mvc.perform(asAdmin(get(REPORTS).param("type", "PROJECT"))).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].target.projectTitle").value("신고된 공고")).andExpect(jsonPath("$.items[0].target.user").doesNotExist());
		mvc.perform(asAdmin(get(REPORTS).param("type", "MEMBER"))).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].target.projectId").value(PROJECT)).andExpect(jsonPath("$.items[0].target.user.tag").value("target"));
		mvc.perform(asAdmin(get(REPORTS).param("type", "USER"))).andExpect(jsonPath("$.totalElements").value(2));
		mvc.perform(asAdmin(get(REPORTS).param("type", "POST"))).andExpect(jsonPath("$.items[0].target.postId").value(POST))
			.andExpect(jsonPath("$.items[0].target.content").value("신고된 글")).andExpect(jsonPath("$.items[0].target.user.nickname").value("대상자"))
			.andExpect(jsonPath("$.items[0].target.deleted").value(false));
		mvc.perform(asAdmin(get(REPORTS).param("type", "COMMENT"))).andExpect(jsonPath("$.items[0].target.commentId").value(COMMENT))
			.andExpect(jsonPath("$.items[0].target.postId").value(POST)).andExpect(jsonPath("$.items[0].target.content").value("신고된 댓글"));
		mvc.perform(asAdmin(get(REPORTS).param("type", "FEED"))).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(get(REPORTS).param("status", "RECEIVED"))).andExpect(status().isBadRequest());
		// 대상이 지워져도 신고 기록은 남고 삭제 표시를 한다.
		jdbc.update("UPDATE posts SET deleted_at = localtimestamp WHERE id = ?", POST);
		jdbc.update("UPDATE projects SET deleted_at = localtimestamp WHERE id = ?", PROJECT);
		mvc.perform(asAdmin(get(REPORTS).param("type", "POST"))).andExpect(jsonPath("$.items[0].target.deleted").value(true));
		mvc.perform(asAdmin(get(REPORTS).param("type", "PROJECT"))).andExpect(jsonPath("$.items[0].target.deleted").value(true));
	}

	@Test
	void handlesWithExistingRulesAndNoAutomaticSanction() throws Exception {
		mvc.perform(handle(R_POST, "{\"status\":\"AWATING\",\"resolution\":\"되돌림\"}")).andExpect(status().isBadRequest());
		mvc.perform(handle(R_POST, "{\"status\":\"RESOLVED\",\"resolution\":\"  \"}")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("처리 결과를 입력해 주세요."));
		mvc.perform(handle(R_POST, "{\"status\":\"RESOLVED\",\"resolution\":\"" + "가".repeat(1001) + "\"}")).andExpect(status().isBadRequest());
		mvc.perform(handle(R_POST, "{\"status\":\"RESOLVED\",\"resolution\":7}")).andExpect(status().isBadRequest());
		mvc.perform(handle(R_POST, "{\"status\":\"REVIEWING\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEWING"));
		mvc.perform(handle(R_POST, "{\"status\":\"RESOLVED\",\"resolution\":\" 게시글 숨김 처리 \",\"handledBy\":3}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RESOLVED")).andExpect(jsonPath("$.resolution").value("게시글 숨김 처리"))
			.andExpect(jsonPath("$.handledByNickname").value("운영자")).andExpect(jsonPath("$.handledAt").isNotEmpty());
		assertEquals(ADMIN, jdbc.queryForObject("SELECT handled_by FROM reports WHERE id = ?", Long.class, R_POST));
		mvc.perform(handle(R_POST, "{\"status\":\"REJECTED\",\"resolution\":\"다시\"}")).andExpect(status().isConflict());
		mvc.perform(handle(999, "{\"status\":\"REJECTED\",\"resolution\":\"없음\"}")).andExpect(status().isNotFound());
		// 신고 처리는 결과만 기록한다.
		assertEquals("VISIBLE", jdbc.queryForObject("SELECT visibility FROM posts WHERE id = ?", String.class, POST));
		assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, TARGET));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM sanctions", Integer.class));
		mvc.perform(asAdmin(get(REPORTS).param("status", "RESOLVED"))).andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(patch(REPORTS + "/" + R_USER).header("Authorization", "Bearer " + jwt.createAccessToken("admin-report2@test.local", "USER"))
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\",\"resolution\":\"x\"}")).andExpect(status().isForbidden());
	}

	@Test
	void concurrentFinalDecisionAppliesOnce() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(3);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		try {
			for (int i = 0; i < 3; i++) {
				Callable<Integer> call = () -> {
					start.await();
					return mvc.perform(handle(R_COMMENT, "{\"status\":\"REJECTED\",\"resolution\":\"광고 아님\"}")).andReturn().getResponse().getStatus();
				};
				results.add(pool.submit(call));
			}
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) statuses.add(result.get());
			assertEquals(List.of(200, 409, 409), statuses.stream().sorted().toList());
		} finally {
			pool.shutdownNow();
		}
	}
}
