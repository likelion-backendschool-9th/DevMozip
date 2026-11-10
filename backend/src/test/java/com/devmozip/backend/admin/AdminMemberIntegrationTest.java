package com.devmozip.backend.admin;

import static org.hamcrest.Matchers.*;
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
import com.devmozip.backend.security.jwt.SuspendedMemberStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * WBS 14.2 회원 조회, 14.3 회원 제재, D8 경고 안내 (docs/admin-plan.md 2단계).
 * 관리자 API 공통 권한(1단계)도 여기서 확인한다. 테이블과 데이터는 이 테스트 전용 PostgreSQL 컨테이너에만 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AdminMemberIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;
	@Autowired PasswordEncoder passwords;
	@Autowired SuspendedMemberStore suspendedMembers;

	static final String USERS = "/api/v1/admin/users";
	static final String PASSWORD = "Devmozip123!";
	static final long ADMIN = 1, OTHER_ADMIN = 2, KIM = 3, LEE = 4, PREMIUM = 5, NO_TAG = 6, WITHDRAWN = 7;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.sql.init.mode", () -> "never");
	}

	@BeforeEach
	void fixtures() {
		jdbc.update("DELETE FROM sanctions");
		jdbc.update("DELETE FROM reports");
		jdbc.update("DELETE FROM users");
		user(ADMIN, "운영자", "admin", "ADMIN", "ACTIVE");
		user(OTHER_ADMIN, "다른운영자", "admin2", "ADMIN", "ACTIVE");
		user(KIM, "김개발", "kim", "USER", "ACTIVE");
		user(LEE, "이%_코드", "lee", "USER", "ACTIVE");
		user(PREMIUM, "박프로", "park", "PREMIUM", "ACTIVE");
		user(NO_TAG, "태그없음", null, "USER", "ACTIVE");
		// 탈퇴 회원(#135): ID·닉네임만 남고 나머지는 NULL이다.
		jdbc.update("INSERT INTO users(id, nickname, is_private) VALUES (?, '탈퇴회원', false)", WITHDRAWN);
	}

	@AfterEach
	void releaseSuspended() {
		for (long id = 1; id <= 6; id++) suspendedMembers.release(email(id));
	}

	static String email(long id) {
		return "admin-it" + id + "@test.local";
	}

	void user(long id, String nickname, String tag, String role, String status) {
		jdbc.update("INSERT INTO users(id, email, password_hash, nickname, tag, role, status, job_categories, skills, collaboration_status, career, is_private)"
			+ " VALUES (?, ?, ?, ?, ?, ?, ?, '[\"백엔드 개발자\"]'::jsonb, '[\"Java\"]'::jsonb, 'AVAILABLE', '2년 차', false)",
			id, email(id), passwords.encode(PASSWORD), nickname, tag, role, status);
	}

	String token(long id, String role) {
		return "Bearer " + jwt.createAccessToken(email(id), role);
	}

	MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
		return request.header("Authorization", token(ADMIN, "ADMIN"));
	}

	MockHttpServletRequestBuilder ban(String slug, String body) {
		return asAdmin(patch(USERS + "/" + slug + "/ban").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	String memberStatus(long id) {
		return jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, id);
	}

	int sanctions() {
		return jdbc.queryForObject("SELECT count(*) FROM sanctions", Integer.class);
	}

	@Test
	void adminApisRequireAdminInTokenAndDatabase() throws Exception {
		mvc.perform(get(USERS)).andExpect(status().isUnauthorized());
		mvc.perform(get(USERS).header("Authorization", token(KIM, "USER"))).andExpect(status().isForbidden());
		mvc.perform(get(USERS).header("Authorization", token(PREMIUM, "PREMIUM"))).andExpect(status().isForbidden());
		// 토큰은 ADMIN이지만 DB 역할이 USER로 바뀐 경우
		mvc.perform(get(USERS).header("Authorization", token(KIM, "ADMIN"))).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("관리자만 이용할 수 있습니다."));
		// DB에서 경고 상태인 관리자
		jdbc.update("UPDATE users SET status = 'WARNED' WHERE id = ?", OTHER_ADMIN);
		mvc.perform(get(USERS).header("Authorization", token(OTHER_ADMIN, "ADMIN"))).andExpect(status().isForbidden());
		mvc.perform(asAdmin(get(USERS))).andExpect(status().isOk());
	}

	@Test
	void listsMembersWithFiltersExcludingWithdrawn() throws Exception {
		mvc.perform(asAdmin(get(USERS))).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(6))
			.andExpect(jsonPath("$.items.length()").value(6))
			.andExpect(jsonPath("$.items[0].id").value(NO_TAG))
			.andExpect(jsonPath("$.items[0].tag").value(nullValue()))
			.andExpect(jsonPath("$.items[*].nickname", not(hasItem("탈퇴회원"))))
			.andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
			.andExpect(jsonPath("$.items[0].kakaoId").doesNotExist())
			.andExpect(jsonPath("$.items[0].latestSanction").value(nullValue()));
		mvc.perform(asAdmin(get(USERS).param("q", "  KIM "))).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].tag").value("kim"));
		mvc.perform(asAdmin(get(USERS).param("q", "admin-it5@"))).andExpect(jsonPath("$.items[0].nickname").value("박프로"));
		// %와 _는 글자 그대로 찾는다.
		mvc.perform(asAdmin(get(USERS).param("q", "%_"))).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].tag").value("lee"));
		mvc.perform(asAdmin(get(USERS).param("role", "ADMIN"))).andExpect(jsonPath("$.totalElements").value(2));
		mvc.perform(asAdmin(get(USERS).param("role", "PREMIUM").param("status", "ACTIVE"))).andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(asAdmin(get(USERS).param("size", "4").param("page", "1"))).andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.totalPages").value(2));
		mvc.perform(asAdmin(get(USERS).param("status", "DELETED"))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("회원 상태를 확인해 주세요."));
		mvc.perform(asAdmin(get(USERS).param("role", "root"))).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(get(USERS).param("size", "51"))).andExpect(status().isBadRequest());
	}

	@Test
	void warnsAndShowsReasonToMemberAndList() throws Exception {
		mvc.perform(ban("kim", "{\"status\":\"WARNED\",\"reason\":\"  운영 정책 위반  \"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("WARNED"))
			.andExpect(jsonPath("$.latestSanction.type").value("WARNED"))
			.andExpect(jsonPath("$.latestSanction.reason").value("운영 정책 위반"))
			.andExpect(jsonPath("$.latestSanction.adminNickname").value("운영자"));
		assertEquals("WARNED", memberStatus(KIM));
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM sanctions WHERE user_id = ? AND admin_id = ? AND type = 'WARNED'",
			Integer.class, KIM, ADMIN));
		mvc.perform(asAdmin(get(USERS).param("status", "WARNED"))).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].latestSanction.reason").value("운영 정책 위반"));
		// 경고 회원은 계속 이용하며, 로그인 확인 응답에 상태와 사유가 온다.
		mvc.perform(get("/api/v1/users/me").header("Authorization", token(KIM, "USER"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value(email(KIM))).andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.status").value("WARNED")).andExpect(jsonPath("$.warningReason").value("운영 정책 위반"));
		mvc.perform(get("/api/v1/users/me").header("Authorization", token(LEE, "USER")))
			.andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.warningReason").value(nullValue()));
		// 해제는 새 행 없이 유효한 제재의 revoked_at을 채운다(ERD). 해제에는 사유·근거 신고를 받지 않는다.
		mvc.perform(ban("kim", "{\"status\":\"ACTIVE\",\"reason\":\"풀어 줌\"}")).andExpect(status().isBadRequest());
		mvc.perform(ban("kim", "{\"status\":\"ACTIVE\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.latestSanction.type").value("WARNED"))
			.andExpect(jsonPath("$.latestSanction.revokedAt").isNotEmpty())
			.andExpect(jsonPath("$.latestSanction.endsAt").value(nullValue()));
		mvc.perform(get("/api/v1/users/me").header("Authorization", token(KIM, "USER")))
			.andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.warningReason").value(nullValue()));
		assertEquals(1, sanctions());
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM sanctions WHERE user_id = ? AND revoked_at IS NOT NULL AND ends_at IS NULL",
			Integer.class, KIM));
	}

	@Test
	void changingSanctionRevokesThePreviousOne() throws Exception {
		mvc.perform(ban("kim", "{\"status\":\"WARNED\",\"reason\":\"1차 경고\"}")).andExpect(status().isOk());
		mvc.perform(ban("kim", "{\"status\":\"SUSPENDED\",\"reason\":\"반복 위반\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.latestSanction.type").value("SUSPENDED")).andExpect(jsonPath("$.latestSanction.revokedAt").value(nullValue()));
		// 경고는 철회되고 정지 하나만 유효하다.
		assertEquals(List.of("WARNED:revoked", "SUSPENDED:active"), jdbc.queryForList(
			"SELECT type || ':' || CASE WHEN revoked_at IS NULL THEN 'active' ELSE 'revoked' END FROM sanctions WHERE user_id = ? ORDER BY id",
			String.class, KIM));
		mvc.perform(ban("kim", "{\"status\":\"ACTIVE\"}")).andExpect(status().isOk());
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM sanctions WHERE user_id = ? AND revoked_at IS NULL", Integer.class, KIM));
		suspendedMembers.release(email(KIM));
	}

	@Test
	void suspensionBlocksExistingTokenAndLoginUntilReleased() throws Exception {
		String kimToken = token(KIM, "USER");
		mvc.perform(get("/api/v1/users/me").header("Authorization", kimToken)).andExpect(status().isOk());
		mvc.perform(ban("kim", "{\"status\":\"SUSPENDED\",\"reason\":\"스팸\"}")).andExpect(status().isOk());
		assertTrue(suspendedMembers.isSuspended(email(KIM)));
		mvc.perform(get("/api/v1/users/me").header("Authorization", kimToken)).andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"" + email(KIM) + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isForbidden());

		// 정지에서 경고로 바꿔도 정지 목록에서 빠진다.
		mvc.perform(ban("kim", "{\"status\":\"WARNED\",\"reason\":\"재발 시 정지\"}")).andExpect(status().isOk());
		assertFalse(suspendedMembers.isSuspended(email(KIM)));
		mvc.perform(get("/api/v1/users/me").header("Authorization", kimToken)).andExpect(status().isOk());
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"" + email(KIM) + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isOk());
	}

	@Test
	void rejectsInvalidRequestsWithoutChanges() throws Exception {
		mvc.perform(ban("kim", "{\"status\":\"WARNED\"}")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("경고·정지 사유를 입력해 주세요."));
		mvc.perform(ban("kim", "{\"status\":\"SUSPENDED\",\"reason\":\"   \"}")).andExpect(status().isBadRequest());
		mvc.perform(ban("kim", "{\"status\":\"WARNED\",\"reason\":\"" + "가".repeat(301) + "\"}")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("사유는 300자 이하로 입력해 주세요."));
		mvc.perform(ban("kim", "{\"status\":\"WARNED\",\"reason\":12}")).andExpect(status().isBadRequest());
		mvc.perform(ban("kim", "{\"status\":\"DELETED\",\"reason\":\"x\"}")).andExpect(status().isBadRequest());
		mvc.perform(ban("kim", "{\"reason\":\"x\"}")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("변경할 상태를 확인해 주세요."));
		mvc.perform(ban("admin", "{\"status\":\"WARNED\",\"reason\":\"x\"}")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("본인 계정은 제재할 수 없습니다."));
		mvc.perform(ban("admin2", "{\"status\":\"SUSPENDED\",\"reason\":\"x\"}")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("관리자 계정은 제재할 수 없습니다."));
		mvc.perform(ban("missing", "{\"status\":\"WARNED\",\"reason\":\"x\"}")).andExpect(status().isNotFound());
		mvc.perform(ban("kim", "{\"status\":\"ACTIVE\"}")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 같은 상태인 회원입니다."));
		mvc.perform(ban("kim", "{\"status\":\"WARNED\",\"reason\":\"x\",\"reportId\":999}")).andExpect(status().isNotFound());
		mvc.perform(patch(USERS + "/kim/ban").header("Authorization", token(LEE, "USER")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"WARNED\",\"reason\":\"x\"}")).andExpect(status().isForbidden());
		assertEquals("ACTIVE", memberStatus(KIM));
		assertEquals(0, sanctions());
		assertFalse(suspendedMembers.isSuspended(email(KIM)));
	}

	@Test
	void recordsReportAndKeepsWithdrawnMembersOutOfReach() throws Exception {
		jdbc.update("INSERT INTO reports(id, reporter_id, reported_user_id, reason, status, created_at, updated_at)"
			+ " VALUES (50, ?, ?, '욕설', 'AWATING', now(), now())", LEE, KIM);
		mvc.perform(ban("kim", "{\"status\":\"WARNED\",\"reason\":\"신고 확인\",\"reportId\":50}")).andExpect(status().isOk());
		assertEquals(50L, jdbc.queryForObject("SELECT report_id FROM sanctions WHERE user_id = ?", Long.class, KIM));
		// 신고 처리 상태는 제재와 따로 바꾼다(자동 연쇄 없음).
		assertEquals("AWATING", jdbc.queryForObject("SELECT status FROM reports WHERE id = 50", String.class));
		// 탈퇴 회원은 태그가 없어 찾을 수 없다. 탈퇴 처리가 끝난 회원의 태그로도 찾지 못한다.
		jdbc.update("UPDATE users SET email = NULL, status = NULL, role = NULL, tag = NULL WHERE id = ?", LEE);
		mvc.perform(ban("lee", "{\"status\":\"WARNED\",\"reason\":\"x\"}")).andExpect(status().isNotFound());
		mvc.perform(asAdmin(get(USERS))).andExpect(jsonPath("$.totalElements").value(5));
	}

	@Test
	void concurrentSameSanctionAppliesOnce() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(4);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		try {
			for (int i = 0; i < 4; i++) {
				Callable<Integer> call = () -> {
					start.await();
					return mvc.perform(ban("kim", "{\"status\":\"SUSPENDED\",\"reason\":\"동시 요청\"}")).andReturn().getResponse().getStatus();
				};
				results.add(pool.submit(call));
			}
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) statuses.add(result.get());
			assertEquals(1, statuses.stream().filter(s -> s == 200).count(), statuses.toString());
			assertEquals(3, statuses.stream().filter(s -> s == 409).count(), statuses.toString());
		} finally {
			pool.shutdownNow();
		}
		assertEquals(1, sanctions());
		assertEquals("SUSPENDED", memberStatus(KIM));
	}
}
