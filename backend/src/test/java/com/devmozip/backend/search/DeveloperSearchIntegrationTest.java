package com.devmozip.backend.search;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.IntStream;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
 * GET /api/v1/search?type=developers (WBS 6.2·6.4·6.5·6.10). JSONB 조건과 ICU 정렬을 쓰므로 H2로 대신하지 않는다.
 * 테이블과 데이터는 이 테스트 전용 PostgreSQL 컨테이너에만 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class DeveloperSearchIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;

	static final String SEARCH = "/api/v1/search";

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.sql.init.mode", () -> "never");
	}

	@BeforeEach
	void fixtures() {
		jdbc.update("DELETE FROM follows");
		jdbc.update("DELETE FROM users");
		// 검색 대상
		user(1, "가나다", "ganada", "ACTIVE", "[\"백엔드 개발자\"]", "3년 차", "[\"Java\", \"Spring Boot\"]", "[\"핀테크\"]", "AVAILABLE");
		user(2, "Zeta", "zeta-dev", "ACTIVE", "[\"프론트엔드 개발자\"]", "1년 차", "[\"React\", \"TypeScript\"]", "[\"교육\", \"AI\"]", "RESTING");
		user(3, "apple", "apple_1", "ACTIVE", "[\"백엔드 개발자\", \"DevOps 엔지니어\"]", "3년 차", "[\"Java\"]", "[\"AI\"]", null);
		user(4, "하늘", "sky", "ACTIVE", "[\"디자이너\"]", "5년 이상", "[\"Figma\"]", "[\"핀테크\"]", "BUSY");
		user(5, "_under", "under", "ACTIVE", "[\"AI 엔지니어\"]", "신입 / 학습 중", "[\"Python\"]", "[]", "AVAILABLE");
		user(6, "백퍼센트100%", "percent", "ACTIVE", "[\"백엔드 개발자\"]", "2년 차", "[\"Kotlin\"]", "[]", "UNAVAILABLE");
		// 검색 대상이 아닌 회원: 경고·정지·태그 없음·빈 태그·직군 없음·직군 값 없음
		user(7, "경고회원", "warned", "WARNED", "[\"백엔드 개발자\"]", "3년 차", "[\"Java\"]", "[]", "AVAILABLE");
		user(8, "정지회원", "suspended", "SUSPENDED", "[\"백엔드 개발자\"]", "3년 차", "[\"Java\"]", "[]", "AVAILABLE");
		user(9, "태그없음", null, "ACTIVE", "[\"백엔드 개발자\"]", "3년 차", "[\"Java\"]", "[]", "AVAILABLE");
		user(10, "빈태그", "", "ACTIVE", "[\"백엔드 개발자\"]", "3년 차", "[\"Java\"]", "[]", "AVAILABLE");
		user(11, "직군없음", "no-job", "ACTIVE", "[]", "3년 차", "[\"Java\"]", "[]", "AVAILABLE");
		user(12, "직군null", "null-job", "ACTIVE", null, "3년 차", "[\"Java\"]", "[]", "AVAILABLE");
	}

	void user(long id, String nickname, String tag, String status, String jobs, String career, String skills, String interests, String collaboration) {
		jdbc.update("INSERT INTO users(id, email, nickname, tag, role, status, job_categories, career, skills, interests,"
				+ " collaboration_status, summary, trust_score, payment_methods, contact_hours)"
				+ " VALUES (?, ?, ?, ?, 'USER', ?, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?, ?, 36.50, '[\"card\"]'::jsonb, '평일 저녁')",
			id, "search" + id + "@test.local", nickname, tag, status, jobs, career, skills, interests, collaboration, nickname + " 소개");
	}

	void follow(long follower, long following) {
		jdbc.update("INSERT INTO follows(follower_id, following_id, activity_notification_enabled, created_at) VALUES (?, ?, false, now())",
			follower, following);
	}

	MockHttpServletRequestBuilder developers() {
		return get(SEARCH).param("type", "developers");
	}

	MockHttpServletRequestBuilder as(long id, MockHttpServletRequestBuilder request) {
		return request.header("Authorization", "Bearer " + jwt.createAccessToken("search" + id + "@test.local", "USER"));
	}

	ResultActions tags(MockHttpServletRequestBuilder request, String... expected) throws Exception {
		return mvc.perform(request).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCount").value(expected.length))
			.andExpect(jsonPath("$.items[*].tag", containsInAnyOrder((Object[]) expected)));
	}

	@Test
	void anonymousSearchListsOnlyProfileReachableMembersInKoreanNicknameOrder() throws Exception {
		mvc.perform(developers())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCount").value(6))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			// 목업의 localeCompare('ko')와 같은 순서: 기호, 숫자, 한글, 영문
			.andExpect(jsonPath("$.items[*].nickname", contains("_under", "가나다", "백퍼센트100%", "하늘", "apple", "Zeta")))
			.andExpect(jsonPath("$.items[1].tag").value("ganada"))
			.andExpect(jsonPath("$.items[1].jobCategories[0]").value("백엔드 개발자"))
			.andExpect(jsonPath("$.items[1].career").value("3년 차"))
			.andExpect(jsonPath("$.items[1].summary").value("가나다 소개"))
			.andExpect(jsonPath("$.items[1].skills", contains("Java", "Spring Boot")))
			.andExpect(jsonPath("$.items[1].interests", contains("핀테크")))
			.andExpect(jsonPath("$.items[1].collaborationStatus").value("AVAILABLE"))
			.andExpect(jsonPath("$.items[1].trustScore").value(36.5))
			.andExpect(jsonPath("$.items[1].profileImage").isEmpty())
			.andExpect(jsonPath("$.items[1].followed").value(false))
			.andExpect(jsonPath("$.items[1].id").doesNotExist())
			.andExpect(jsonPath("$.items[1].email").doesNotExist())
			.andExpect(jsonPath("$.items[1].contactHours").doesNotExist())
			.andExpect(jsonPath("$.items[1].paymentMethods").doesNotExist());
		// 만료·위조 토큰이 있어도 공개 검색은 비로그인으로 처리한다.
		mvc.perform(developers().header("Authorization", "Bearer invalid")).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCount").value(6));
	}

	@Test
	void everyKeywordWordMustAppearSomewhereIgnoringCase() throws Exception {
		tags(developers().param("q", "java 3년"), "ganada", "apple_1");
		tags(developers().param("q", "JAVA"), "ganada", "apple_1");
		// 전각 문자는 NFKC로 바꿔 찾는다.
		tags(developers().param("q", "ＪＡＶＡ"), "ganada", "apple_1");
		tags(developers().param("q", "zeta-dev"), "zeta-dev");
		tags(developers().param("q", "핀테크 figma"), "sky");
		tags(developers().param("q", "  devops   백엔드  "), "apple_1");
		tags(developers().param("q", "신입"), "under");
		tags(developers().param("q", "java python"));
		// LIKE의 %·_는 글자 그대로 찾는다.
		tags(developers().param("q", "%"), "percent");
		tags(developers().param("q", "_"), "under", "apple_1");
		tags(developers().param("q", "' OR 1=1 --"));
		// 한 줄 소개는 검색어 대상이 아니다.
		tags(developers().param("q", "소개"));
	}

	@Test
	void filtersCombineWithAndInterestsMatchAnySkillsMatchAll() throws Exception {
		tags(developers().param("jobCategory", "백엔드 개발자"), "ganada", "apple_1", "percent");
		tags(developers().param("career", "3년 차"), "ganada", "apple_1");
		tags(developers().param("interests", "핀테크", "교육"), "ganada", "zeta-dev", "sky");
		tags(developers().param("skills", "Java", "Spring Boot"), "ganada");
		tags(developers().param("skills", "Java"), "ganada", "apple_1");
		tags(developers().param("available", "true"), "ganada", "under");
		// 협업 가능이 아닌 상태와 값 없음은 모두 협업 쉬는 중이다.
		tags(developers().param("available", "false"), "zeta-dev", "apple_1", "sky", "percent");
		tags(developers().param("jobCategory", "백엔드 개발자").param("available", "false"), "apple_1", "percent");
		tags(developers().param("q", "java").param("interests", "AI").param("career", "3년 차"), "apple_1");
		// 목록에 없는 문구는 거절하지 않고 결과가 없다. 값 하나에 든 쉼표는 나누지 않는다.
		tags(developers().param("jobCategory", "없는 직군"));
		tags(developers().param("skills", "Java,Spring Boot"));
		// 빈 값은 조건이 없는 것으로 본다.
		tags(developers().param("q", " ").param("available", "").param("jobCategory", ""),
			"ganada", "zeta-dev", "apple_1", "sky", "under", "percent");
	}

	@Test
	void pagesKeepTotalsAndPageBeyondEndIsEmpty() throws Exception {
		mvc.perform(developers().param("size", "4").param("page", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].nickname", contains("apple", "Zeta")))
			.andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(4))
			.andExpect(jsonPath("$.totalCount").value(6)).andExpect(jsonPath("$.totalPages").value(2));
		mvc.perform(developers().param("size", "4").param("page", "5"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.totalCount").value(6)).andExpect(jsonPath("$.totalPages").value(2));
		mvc.perform(developers().param("jobCategory", "없는 직군"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalPages").value(0));
		mvc.perform(developers().param("size", "100")).andExpect(status().isOk());
	}

	@Test
	void invalidRequestsAnswer400InCommonErrorFormat() throws Exception {
		String longQuery = "가".repeat(101);
		String longValue = "a".repeat(31);
		String[] manyValues = IntStream.range(0, 31).mapToObj(i -> "v" + i).toArray(String[]::new);
		MockHttpServletRequestBuilder[] requests = {
			get(SEARCH),
			get(SEARCH).param("type", "users"),
			get(SEARCH).param("type", "DEVELOPERS"),
			get(SEARCH).param("type", "developers", "projects"),
			developers().param("q", longQuery),
			developers().param("q", "a", "b"),
			developers().param("jobCategory", longValue),
			developers().param("career", longValue),
			developers().param("skills", longValue),
			developers().param("interests", manyValues),
			developers().param("available", "yes"),
			developers().param("following", "1"),
			developers().param("page", "-1"),
			developers().param("page", "abc"),
			developers().param("size", "0"),
			developers().param("size", "101"),
		};
		for (MockHttpServletRequestBuilder request : requests) {
			mvc.perform(request).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("BAD_REQUEST"))
				.andExpect(jsonPath("$.message").isNotEmpty())
				.andExpect(jsonPath("$.path").value(SEARCH));
		}
		// 100자와 30개는 받는다.
		mvc.perform(developers().param("q", "가".repeat(100))).andExpect(status().isOk());
		mvc.perform(developers().param("skills", IntStream.range(0, 30).mapToObj(i -> "v" + i).toArray(String[]::new)))
			.andExpect(status().isOk());
	}

	@Test
	void followingTabNeedsLoginAndListsOnlyRequesterFollowings() throws Exception {
		mvc.perform(developers().param("following", "true"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.message").isNotEmpty());

		follow(1, 2);
		follow(1, 4);
		// 검색 대상이 아닌 회원은 팔로우했어도 나오지 않는다.
		follow(1, 7);
		follow(1, 9);
		// 다른 회원 사이의 팔로우 관계는 요청자 기준 값에 반영하지 않는다.
		follow(3, 5);
		follow(2, 1);

		tags(as(1, developers().param("following", "true")), "zeta-dev", "sky")
			.andExpect(jsonPath("$.items[*].followed", contains(true, true)));
		// 쿼리의 사용자 ID는 쓰지 않는다.
		tags(as(1, developers().param("following", "true").param("userId", "3").param("followerId", "3")), "zeta-dev", "sky");
		tags(as(1, developers().param("following", "true").param("skills", "Figma")), "sky");
		tags(as(1, developers().param("following", "true").param("q", "react")), "zeta-dev");
		tags(as(1, developers().param("following", "true").param("available", "true")));
		// following=false는 전체 탭과 같다.
		tags(as(1, developers().param("following", "false")), "ganada", "zeta-dev", "apple_1", "sky", "under", "percent");

		// 전체 탭: 본인도 나오고, followed는 요청자가 팔로우한 회원만 true다.
		mvc.perform(as(1, developers()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].tag", contains("under", "ganada", "percent", "sky", "apple_1", "zeta-dev")))
			.andExpect(jsonPath("$.items[*].followed", contains(false, false, false, true, false, true)));
		mvc.perform(developers())
			.andExpect(jsonPath("$.items[*].followed", contains(false, false, false, false, false, false)));

		// 언팔로우한 회원은 팔로잉 탭에서 빠진다.
		jdbc.update("DELETE FROM follows WHERE follower_id = 1 AND following_id = 4");
		tags(as(1, developers().param("following", "true")), "zeta-dev");
		// 아무도 팔로우하지 않았으면 빈 목록이다. 자기 자신은 팔로우할 수 없으므로 본인은 나오지 않는다.
		tags(as(5, developers().param("following", "true")));
	}
}
