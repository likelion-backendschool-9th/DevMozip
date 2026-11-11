package com.devmozip.backend.follow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 팔로우 API의 비공개 계정 처리(docs/privacy-settings-plan.md 4단계). 본인·다른 회원·비로그인 × 공개·비공개 계정.
 * 테이블과 데이터는 이 테스트 전용 PostgreSQL 컨테이너에만 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FollowPrivacyIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;

	static final String USERS = "/api/v1/users/";
	static final String PRIVATE = "비공개 계정입니다.";
	static final long VIEWER = 1, TARGET = 2, OTHER = 3, PRIVATE_FOLLOWER = 4, PUBLIC_FOLLOWER = 5;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.sql.init.mode", () -> "never");
	}

	@BeforeEach
	void fixtures() {
		jdbc.update("DELETE FROM follows");
		jdbc.update("DELETE FROM users");
		user(VIEWER, "기준회원", "viewer");
		user(TARGET, "대상회원", "target");
		user(OTHER, "다른회원", "other");
		user(PRIVATE_FOLLOWER, "비공개회원", "private-follower");
		user(PUBLIC_FOLLOWER, "공개회원", "public-follower");
		// OTHER의 팔로워: 기준회원, 비공개회원, 공개회원(최근 순서). TARGET은 OTHER와 비공개회원을 팔로우한다.
		follow(VIEWER, OTHER, "2026-11-01 10:00:00");
		follow(PRIVATE_FOLLOWER, OTHER, "2026-11-02 10:00:00");
		follow(PUBLIC_FOLLOWER, OTHER, "2026-11-03 10:00:00");
		follow(VIEWER, TARGET, "2026-11-04 10:00:00");
		follow(TARGET, OTHER, "2026-11-05 10:00:00");
		follow(TARGET, PRIVATE_FOLLOWER, "2026-11-06 10:00:00");
		privacy(PRIVATE_FOLLOWER, true);
	}

	void user(long id, String nickname, String tag) {
		jdbc.update("INSERT INTO users(id, email, nickname, tag, role, status, job_categories, skills, collaboration_status, career)"
			+ " VALUES (?, ?, ?, ?, 'USER', 'ACTIVE', '[\"백엔드 개발자\"]'::jsonb, '[\"Java\"]'::jsonb, 'AVAILABLE', '2년 차')",
			id, "privacy" + id + "@test.local", nickname, tag);
	}

	void follow(long follower, long following, String createdAt) {
		jdbc.update("INSERT INTO follows(follower_id, following_id, activity_notification_enabled, created_at) VALUES (?, ?, false, ?::timestamp)",
			follower, following, createdAt);
	}

	void privacy(long id, boolean isPrivate) {
		jdbc.update("UPDATE users SET is_private = ? WHERE id = ?", isPrivate, id);
	}

	MockHttpServletRequestBuilder as(Long id, MockHttpServletRequestBuilder request) {
		return id == null ? request : request.header("Authorization", "Bearer " + jwt.createAccessToken("privacy" + id + "@test.local", "USER"));
	}

	ResultActions forbiddenAsPrivate(ResultActions actions) throws Exception {
		return actions.andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403)).andExpect(jsonPath("$.message").value(PRIVATE));
	}

	int relations() {
		return jdbc.queryForObject("SELECT count(*) FROM follows", Integer.class);
	}

	@Test
	void privateAccountIsForbiddenToOthersAndAnonymous() throws Exception {
		privacy(TARGET, true);
		for (Long requester : new Long[] {null, VIEWER, PRIVATE_FOLLOWER}) {
			forbiddenAsPrivate(mvc.perform(as(requester, get(USERS + "target/follow"))));
			forbiddenAsPrivate(mvc.perform(as(requester, get(USERS + "target/followers"))));
			forbiddenAsPrivate(mvc.perform(as(requester, get(USERS + "target/following"))));
		}
		// 이미 팔로우한 회원도 비공개인 동안은 팔로우·언팔로우할 수 없다. 관계는 그대로 남는다.
		forbiddenAsPrivate(mvc.perform(as(VIEWER, post(USERS + "target/follow"))));
		forbiddenAsPrivate(mvc.perform(as(VIEWER, delete(USERS + "target/follow"))));
		forbiddenAsPrivate(mvc.perform(as(OTHER, post(USERS + "target/follow"))));
		assertEquals(6, relations());
		// 비로그인 변경 요청은 공개 여부보다 로그인 확인이 먼저다.
		mvc.perform(post(USERS + "target/follow")).andExpect(status().isUnauthorized());
		// 없는 사용자는 공개 여부와 관계없이 404다.
		mvc.perform(get(USERS + "missing/follow")).andExpect(status().isNotFound());
	}

	@Test
	void ownerSeesOwnPrivateAccount() throws Exception {
		privacy(TARGET, true);
		mvc.perform(as(TARGET, get(USERS + "target/follow"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.followerCount").value(1)).andExpect(jsonPath("$.followingCount").value(2));
		mvc.perform(as(TARGET, get(USERS + "target/followers"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].tag").value("viewer"));
		// 본인 목록이어도 다른 회원의 비공개 계정은 항목에서 빠진다(Mock canViewProfile과 같다).
		mvc.perform(as(TARGET, get(USERS + "target/following"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].tag").value("other"));
		// 자기 자신 팔로우는 공개 여부와 관계없이 기존처럼 400이다.
		mvc.perform(as(TARGET, post(USERS + "target/follow"))).andExpect(status().isBadRequest());
	}

	@Test
	void listItemsExcludePrivateAccountsExceptRequester() throws Exception {
		// 비로그인과 다른 회원에게는 비공개 회원이 빠지고, 전체 수와 다음 페이지도 뺀 뒤의 값이다.
		for (Long requester : new Long[] {null, VIEWER}) {
			mvc.perform(as(requester, get(USERS + "other/followers"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCount").value(3))
				.andExpect(jsonPath("$.items.length()").value(3))
				.andExpect(jsonPath("$.items[0].tag").value("target"))
				.andExpect(jsonPath("$.items[1].tag").value("public-follower"))
				.andExpect(jsonPath("$.items[2].tag").value("viewer"));
			mvc.perform(as(requester, get(USERS + "other/followers").param("size", "2"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.hasNext").value(true)).andExpect(jsonPath("$.totalCount").value(3));
			mvc.perform(as(requester, get(USERS + "other/followers").param("size", "2").param("page", "1"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].tag").value("viewer"))
				.andExpect(jsonPath("$.hasNext").value(false));
			mvc.perform(as(requester, get(USERS + "other/followers").param("q", "비공개"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCount").value(0)).andExpect(jsonPath("$.items").isEmpty());
		}
		// 비공개 회원 본인에게는 자기 항목이 보인다.
		mvc.perform(as(PRIVATE_FOLLOWER, get(USERS + "other/followers"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCount").value(4)).andExpect(jsonPath("$.items[2].tag").value("private-follower"));
		mvc.perform(as(PRIVATE_FOLLOWER, get(USERS + "other/followers").param("size", "2"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.hasNext").value(true)).andExpect(jsonPath("$.totalCount").value(4));
	}

	@Test
	void countsKeepAllRelations() throws Exception {
		// 탭의 수는 기존 계약대로 모든 관계를 센다. Mock 목록 화면도 수는 전체이고 항목만 비공개 계정을 뺐다.
		mvc.perform(get(USERS + "other/follow")).andExpect(status().isOk()).andExpect(jsonPath("$.followerCount").value(4));
		mvc.perform(as(VIEWER, post(USERS + "other/follow"))).andExpect(status().isOk()).andExpect(jsonPath("$.followerCount").value(4));
	}

	@Test
	void privateRequesterCanStillFollowPublicAccounts() throws Exception {
		mvc.perform(as(PRIVATE_FOLLOWER, post(USERS + "viewer/follow"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.followed").value(true)).andExpect(jsonPath("$.followerCount").value(1));
		mvc.perform(get(USERS + "viewer/followers")).andExpect(jsonPath("$.totalCount").value(0));
		mvc.perform(as(PRIVATE_FOLLOWER, get(USERS + "viewer/followers"))).andExpect(jsonPath("$.totalCount").value(1));
		mvc.perform(as(PRIVATE_FOLLOWER, delete(USERS + "viewer/follow"))).andExpect(status().isOk()).andExpect(jsonPath("$.followed").value(false));
	}

	@Test
	void notificationSettingDoesNotDependOnPrivacy() throws Exception {
		// 알림 설정은 요청자 자신의 팔로우 행만 바꾸고 대상 정보를 돌려주지 않는다.
		privacy(TARGET, true);
		mvc.perform(as(VIEWER, put(USERS + "target/notify").contentType(MediaType.APPLICATION_JSON).content("{\"activityNotificationEnabled\":true}")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.activityNotificationEnabled").value(true));
	}

	@Test
	void settingChangeAppliesFromNextRequest() throws Exception {
		String token = "Bearer " + jwt.createAccessToken("privacy2@test.local", "USER");
		mvc.perform(patch("/api/v1/users/me/account-lock").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{\"isPrivate\":true}"))
			.andExpect(status().isOk());
		forbiddenAsPrivate(mvc.perform(as(VIEWER, get(USERS + "target/followers"))));
		mvc.perform(as(VIEWER, get(USERS + "other/followers"))).andExpect(jsonPath("$.totalCount").value(2));

		mvc.perform(patch("/api/v1/users/me/account-lock").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{\"isPrivate\":false}"))
			.andExpect(status().isOk());
		mvc.perform(as(VIEWER, get(USERS + "target/followers"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1));
		mvc.perform(as(VIEWER, get(USERS + "other/followers"))).andExpect(jsonPath("$.totalCount").value(3));
		privacy(PRIVATE_FOLLOWER, false);
		mvc.perform(as(VIEWER, get(USERS + "other/followers"))).andExpect(jsonPath("$.totalCount").value(4));
		mvc.perform(as(VIEWER, get(USERS + "target/following"))).andExpect(jsonPath("$.totalCount").value(2));
	}
}
