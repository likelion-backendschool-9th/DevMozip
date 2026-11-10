package com.devmozip.backend.follow;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

/** WBS 9.1~9.5와 팔로우 상태 API. 테이블과 데이터는 이 테스트 전용 PostgreSQL 컨테이너에만 만든다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FollowApiIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;

	static final String USERS = "/api/v1/users/";

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.sql.init.mode", () -> "never");
	}

	@BeforeEach
	void fixtures() {
		jdbc.update("DELETE FROM follows");
		jdbc.update("DELETE FROM users");
		user(1, "기준회원", "follow-me", "ACTIVE", "[\"백엔드 개발자\"]", "[\"Java\"]", "AVAILABLE");
		user(2, "대상회원", "follow-target", "ACTIVE", "[\"프론트엔드 개발자\"]", "[\"React\"]", "AVAILABLE");
		user(3, "경고회원", "warned-user", "WARNED", "[\"데이터 엔지니어\"]", "[\"Python\"]", "BUSY");
		user(4, "태그없음", null, "ACTIVE", "[\"백엔드 개발자\"]", "[]", null);
		user(5, "정지회원", "suspended-user", "SUSPENDED", "[\"백엔드 개발자\"]", "[]", "AVAILABLE");
		user(6, "직군없음", "no-job", "ACTIVE", "[]", "[]", "AVAILABLE");
		user(7, "다른회원", "other-user", "ACTIVE", "[\"백엔드 개발자\"]", "[\"Kotlin\"]", "AVAILABLE");
	}

	void user(long id, String nickname, String tag, String status, String jobs, String skills, String collaboration) {
		jdbc.update("INSERT INTO users(id, email, nickname, tag, role, status, job_categories, skills, collaboration_status, career, payment_methods)"
			+ " VALUES (?, ?, ?, ?, 'USER', ?, ?::jsonb, ?::jsonb, ?, '2년 차', '[\"card\"]'::jsonb)",
			id, "follow" + id + "@test.local", nickname, tag, status, jobs, skills, collaboration);
	}

	void follow(long follower, long following, String createdAt, boolean notification) {
		jdbc.update("INSERT INTO follows(follower_id, following_id, activity_notification_enabled, created_at) VALUES (?, ?, ?, ?::timestamp)",
			follower, following, notification, createdAt);
	}

	String token(long id) {
		return "Bearer " + jwt.createAccessToken("follow" + id + "@test.local", "USER");
	}

	MockHttpServletRequestBuilder as(long id, MockHttpServletRequestBuilder request) {
		return request.header("Authorization", token(id));
	}

	MockHttpServletRequestBuilder notification(String slug, String body) {
		return put(USERS + slug + "/notify").contentType(MediaType.APPLICATION_JSON).content(body);
	}

	int relations() {
		return jdbc.queryForObject("SELECT count(*) FROM follows", Integer.class);
	}

	@Test
	void anonymousCanReadButNotChange() throws Exception {
		mvc.perform(get(USERS + "follow-target/follow")).andExpect(status().isOk())
			.andExpect(jsonPath("$.followerCount").value(0)).andExpect(jsonPath("$.followingCount").value(0))
			.andExpect(jsonPath("$.followed").value(false)).andExpect(jsonPath("$.activityNotificationEnabled").value(false));
		mvc.perform(get(USERS + "follow-target/followers")).andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalCount").value(0))
			.andExpect(jsonPath("$.hasNext").value(false)).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
		mvc.perform(get(USERS + "follow-target/following")).andExpect(status().isOk());
		// 만료·위조 토큰이 있어도 공개 조회는 비로그인으로 처리한다.
		mvc.perform(get(USERS + "follow-target/follow").header("Authorization", "Bearer invalid")).andExpect(status().isOk());

		mvc.perform(post(USERS + "follow-target/follow")).andExpect(status().isUnauthorized());
		mvc.perform(delete(USERS + "follow-target/follow")).andExpect(status().isUnauthorized());
		mvc.perform(notification("follow-target", "{\"activityNotificationEnabled\":true}")).andExpect(status().isUnauthorized());
		assertEquals(0, relations());
	}

	@Test
	void followAndUnfollowAnswerCurrentStateWhenRepeated() throws Exception {
		for (int attempt = 0; attempt < 2; attempt++) {
			// 요청 값의 사용자 ID는 쓰지 않고 토큰의 회원으로 팔로우한다.
			mvc.perform(as(1, post(USERS + "follow-target/follow").param("userId", "3").param("followerId", "3")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.followed").value(true))
				.andExpect(jsonPath("$.activityNotificationEnabled").value(false))
				.andExpect(jsonPath("$.followerCount").value(1));
		}
		assertEquals(1, relations());
		assertEquals(1, jdbc.queryForObject("SELECT follower_id FROM follows", Integer.class));
		mvc.perform(as(1, get(USERS + "follow-target/follow")))
			.andExpect(jsonPath("$.followed").value(true)).andExpect(jsonPath("$.followerCount").value(1));
		mvc.perform(get(USERS + "follow-target/follow")).andExpect(jsonPath("$.followed").value(false));
		mvc.perform(get(USERS + "follow-me/follow"))
			.andExpect(jsonPath("$.followingCount").value(1)).andExpect(jsonPath("$.followerCount").value(0));

		for (int attempt = 0; attempt < 2; attempt++) {
			mvc.perform(as(1, delete(USERS + "follow-target/follow")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.followed").value(false))
				.andExpect(jsonPath("$.activityNotificationEnabled").value(false))
				.andExpect(jsonPath("$.followerCount").value(0));
		}
		assertEquals(0, relations());
	}

	@Test
	void concurrentFollowRequestsKeepOneRelationAndAllAnswerFollowed() throws Exception {
		int requests = 8;
		ExecutorService executor = Executors.newFixedThreadPool(requests);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<String>> results = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				Callable<String> call = () -> {
					start.await();
					var response = mvc.perform(as(1, post(USERS + "follow-target/follow"))).andReturn().getResponse();
					return response.getStatus() + " " + response.getContentAsString();
				};
				results.add(executor.submit(call));
			}
			start.countDown();
			for (Future<String> result : results) {
				String value = result.get();
				assertTrue(value.startsWith("200 "), value);
				assertTrue(value.contains("\"followed\":true"), value);
				assertTrue(value.contains("\"followerCount\":1"), value);
			}
		} finally {
			executor.shutdownNow();
		}
		assertEquals(1, relations());
	}

	@Test
	void rejectsSelfAndTargetsOutsideProfileConditions() throws Exception {
		mvc.perform(as(1, post(USERS + "follow-me/follow"))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("자기 자신은 팔로우할 수 없습니다."));
		mvc.perform(as(1, delete(USERS + "follow-me/follow"))).andExpect(status().isBadRequest());
		mvc.perform(as(1, notification("follow-me", "{\"activityNotificationEnabled\":true}"))).andExpect(status().isBadRequest());
		for (String slug : List.of("unknown-user", "suspended-user", "no-job", "me", "FOLLOW-TARGET")) {
			mvc.perform(as(1, post(USERS + slug + "/follow"))).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."));
			mvc.perform(get(USERS + slug + "/follow")).andExpect(status().isNotFound());
			mvc.perform(get(USERS + slug + "/followers")).andExpect(status().isNotFound());
			mvc.perform(get(USERS + slug + "/following")).andExpect(status().isNotFound());
		}
		assertEquals(0, relations());
	}

	@Test
	void writeRequestsNeedActiveOrWarnedAccount() throws Exception {
		mvc.perform(as(3, post(USERS + "follow-target/follow"))).andExpect(status().isOk());
		// 테스트 데이터는 서버 시작 뒤에 넣어 JWT 필터의 정지 목록에 없으므로 Service의 상태 확인까지 온다.
		mvc.perform(as(5, post(USERS + "follow-target/follow"))).andExpect(status().isForbidden());
		mvc.perform(as(99, post(USERS + "follow-target/follow"))).andExpect(status().isUnauthorized());
		assertEquals(1, relations());
	}

	@Test
	void notificationNeedsFollowAndIsRemovedWithUnfollow() throws Exception {
		mvc.perform(as(1, notification("follow-target", "{\"activityNotificationEnabled\":true}"))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("팔로우한 개발자의 알림만 설정할 수 있습니다."));

		mvc.perform(as(1, post(USERS + "follow-target/follow"))).andExpect(status().isOk());
		mvc.perform(as(1, notification("follow-target", "{\"activityNotificationEnabled\":true}"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.activityNotificationEnabled").value(true));
		mvc.perform(as(1, get(USERS + "follow-target/follow"))).andExpect(jsonPath("$.activityNotificationEnabled").value(true));
		mvc.perform(as(1, get(USERS + "follow-me/following")))
			.andExpect(jsonPath("$.items[0].tag").value("follow-target"))
			.andExpect(jsonPath("$.items[0].followed").value(true))
			.andExpect(jsonPath("$.items[0].activityNotificationEnabled").value(true));
		mvc.perform(as(1, post(USERS + "follow-target/follow"))).andExpect(jsonPath("$.activityNotificationEnabled").value(true));

		for (String body : List.of("{}", "{\"activityNotificationEnabled\":null}", "{", "[]")) {
			mvc.perform(as(1, notification("follow-target", body))).andExpect(status().isBadRequest());
		}
		mvc.perform(as(1, put(USERS + "follow-target/notify").contentType(MediaType.TEXT_PLAIN).content("true")))
			.andExpect(status().isUnsupportedMediaType());

		mvc.perform(as(1, delete(USERS + "follow-target/follow"))).andExpect(status().isOk());
		mvc.perform(as(1, post(USERS + "follow-target/follow"))).andExpect(jsonPath("$.activityNotificationEnabled").value(false));
	}

	@Test
	void listsAndCountsIncludeEveryRelationWithoutAccountFields() throws Exception {
		follow(3, 2, "2026-11-01 10:00:00", false);
		follow(4, 2, "2026-11-02 10:00:00", false);
		follow(5, 2, "2026-11-03 10:00:00", false);
		follow(6, 2, "2026-11-04 10:00:00", false);
		follow(7, 2, "2026-11-05 10:00:00", false);
		mvc.perform(get(USERS + "follow-target/follow")).andExpect(jsonPath("$.followerCount").value(5));
		mvc.perform(get(USERS + "follow-target/followers")).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCount").value(5))
			.andExpect(jsonPath("$.items.length()").value(5))
			.andExpect(jsonPath("$.items[0].tag").value("other-user"))
			.andExpect(jsonPath("$.items[0].nickname").value("다른회원"))
			.andExpect(jsonPath("$.items[0].jobCategories[0]").value("백엔드 개발자"))
			.andExpect(jsonPath("$.items[0].skills[0]").value("Kotlin"))
			.andExpect(jsonPath("$.items[0].career").value("2년 차"))
			.andExpect(jsonPath("$.items[0].collaborationStatus").value("AVAILABLE"))
			.andExpect(jsonPath("$.items[0].profileImage").isEmpty())
			.andExpect(jsonPath("$.items[1].tag").value("no-job"))
			.andExpect(jsonPath("$.items[1].jobCategories").isEmpty())
			.andExpect(jsonPath("$.items[2].tag").value("suspended-user"))
			.andExpect(jsonPath("$.items[3].tag").isEmpty())
			.andExpect(jsonPath("$.items[3].collaborationStatus").isEmpty())
			.andExpect(jsonPath("$.items[0].id").doesNotExist())
			.andExpect(jsonPath("$.items[0].email").doesNotExist())
			.andExpect(jsonPath("$.items[0].paymentMethods").doesNotExist())
			.andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
		mvc.perform(get(USERS + "other-user/following")).andExpect(jsonPath("$.items[0].tag").value("follow-target"));
	}

	@Test
	void followedFlagsFollowTheRequesterOnly() throws Exception {
		follow(3, 2, "2026-11-01 10:00:00", false);
		follow(4, 2, "2026-11-02 10:00:00", false);
		follow(7, 2, "2026-11-03 10:00:00", false);
		follow(1, 3, "2026-11-04 10:00:00", true);
		follow(1, 7, "2026-11-04 10:00:00", false);
		// 7번 회원이 3번을 팔로우해도 요청자(1번)의 값에는 섞이지 않는다.
		follow(7, 3, "2026-11-04 10:00:00", true);

		mvc.perform(as(1, get(USERS + "follow-target/followers"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].tag").value("other-user"))
			.andExpect(jsonPath("$.items[0].followed").value(true))
			.andExpect(jsonPath("$.items[0].activityNotificationEnabled").value(false))
			.andExpect(jsonPath("$.items[1].followed").value(false))
			.andExpect(jsonPath("$.items[2].tag").value("warned-user"))
			.andExpect(jsonPath("$.items[2].followed").value(true))
			.andExpect(jsonPath("$.items[2].activityNotificationEnabled").value(true));
		mvc.perform(get(USERS + "follow-target/followers"))
			.andExpect(jsonPath("$.items[0].followed").value(false))
			.andExpect(jsonPath("$.items[2].followed").value(false))
			.andExpect(jsonPath("$.items[2].activityNotificationEnabled").value(false));
		// 목록에 요청자 본인이 있으면 자기 자신과의 관계가 없으므로 false다.
		mvc.perform(as(7, get(USERS + "follow-target/followers")))
			.andExpect(jsonPath("$.items[0].tag").value("other-user"))
			.andExpect(jsonPath("$.items[0].followed").value(false))
			.andExpect(jsonPath("$.items[2].followed").value(true));
		// 본인 프로필의 팔로우 상태도 false다.
		mvc.perform(as(2, get(USERS + "follow-target/follow")))
			.andExpect(jsonPath("$.followed").value(false)).andExpect(jsonPath("$.followerCount").value(3));
	}

	@Test
	void searchesFiltersAndPages() throws Exception {
		follow(1, 2, "2026-11-01 10:00:00", false);
		follow(1, 3, "2026-11-02 10:00:00", false);
		follow(1, 4, "2026-11-03 10:00:00", false);
		follow(1, 7, "2026-11-04 10:00:00", false);
		String following = USERS + "follow-me/following";
		mvc.perform(get(following).param("q", " KOTLIN ")).andExpect(jsonPath("$.totalCount").value(1))
			.andExpect(jsonPath("$.items[0].tag").value("other-user"));
		mvc.perform(get(following).param("q", "react")).andExpect(jsonPath("$.items[0].tag").value("follow-target"));
		mvc.perform(get(following).param("q", "데이터")).andExpect(jsonPath("$.items[0].tag").value("warned-user"));
		mvc.perform(get(following).param("q", "warned")).andExpect(jsonPath("$.items[0].nickname").value("경고회원"));
		mvc.perform(get(following).param("q", "   ")).andExpect(jsonPath("$.totalCount").value(4));
		mvc.perform(get(following).param("available", "true")).andExpect(jsonPath("$.totalCount").value(2))
			.andExpect(jsonPath("$.items[0].tag").value("other-user")).andExpect(jsonPath("$.items[1].tag").value("follow-target"));
		mvc.perform(get(following).param("available", "true").param("q", "kotlin")).andExpect(jsonPath("$.totalCount").value(1));

		mvc.perform(get(following).param("size", "3")).andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.hasNext").value(true)).andExpect(jsonPath("$.totalCount").value(4));
		mvc.perform(get(following).param("size", "3").param("page", "1")).andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].tag").value("follow-target")).andExpect(jsonPath("$.hasNext").value(false))
			.andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(3));
		mvc.perform(get(following).param("page", "9")).andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.totalCount").value(4)).andExpect(jsonPath("$.hasNext").value(false));
		mvc.perform(get(following).param("q", "가".repeat(100))).andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = {"page=-1", "size=0", "size=101", "page=abc", "size=oops", "available=maybe"})
	void rejectsInvalidListConditions(String query) throws Exception {
		mvc.perform(get(USERS + "follow-me/followers?" + query)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.message").exists());
	}

	@Test
	void rejectsTooLongSearch() throws Exception {
		mvc.perform(get(USERS + "follow-me/following").param("q", "가".repeat(101))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("검색어는 100자 이내로 입력해 주세요."));
	}
}
