package com.devmozip.backend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberRole;
import com.devmozip.backend.entity.Member.MemberStatus;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.security.jwt.SuspendedMemberStore;
import com.devmozip.backend.service.AccountVisibilityPolicy;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** WBS 17.7 계정 잠금(비공개 계정) 조회·변경 API. 회원은 이 테스트 전용 PostgreSQL 컨테이너에만 만든다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AccountLockIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;
	@Autowired MemberRepository members;
	@Autowired SuspendedMemberStore suspended;

	static final String URL = "/api/v1/users/me/account-lock";

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.sql.init.mode", () -> "never");
	}

	@BeforeEach
	void clean() {
		jdbc.update("DELETE FROM users");
	}

	Member member(String email, String nickname) {
		return members.saveAndFlush(Member.builder().email(email).nickname(nickname)
			.role(MemberRole.USER).status(MemberStatus.ACTIVE).build());
	}

	MockHttpServletRequestBuilder as(String email, MockHttpServletRequestBuilder request) {
		return request.header("Authorization", "Bearer " + jwt.createAccessToken(email, "USER"));
	}

	MockHttpServletRequestBuilder change(String email, String body) {
		return as(email, patch(URL).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	boolean stored(String email) {
		return jdbc.queryForObject("SELECT is_private FROM users WHERE email = ?", Boolean.class, email);
	}

	@Test
	void anonymousCannotReadOrChange() throws Exception {
		mvc.perform(get(URL)).andExpect(status().isUnauthorized());
		mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"isPrivate\":true}"))
			.andExpect(status().isUnauthorized());
		mvc.perform(get(URL).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
	}

	@Test
	void newMembersArePublic() throws Exception {
		// 가입 API는 공개 여부를 받지 않고 바꾸지 않았다. 새 회원은 기본값으로 공개 계정이다.
		mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
			{"email":"new@test.local","password":"Devmozip123","confirmPassword":"Devmozip123","nickname":"새회원",
			 "job":"백엔드 개발자","career":"2년 차","skills":["Java"],"interests":["교육"],"available":true}
			""")).andExpect(status().isCreated());
		assertFalse(stored("new@test.local"));
		mvc.perform(as("new@test.local", get(URL))).andExpect(status().isOk()).andExpect(jsonPath("$.isPrivate").value(false));

		member("builder@test.local", "빌더회원");
		assertFalse(stored("builder@test.local"));

		// SQL로 컬럼 없이 넣은 행도 DB 기본값으로 공개 계정이 된다(다른 테스트·초기 데이터 SQL이 이렇게 넣는다).
		jdbc.update("INSERT INTO users(email, nickname, role, status) VALUES ('sql@test.local', 'SQL회원', 'USER', 'ACTIVE')");
		assertFalse(stored("sql@test.local"));
	}

	@Test
	void changeIsIdempotentAndOnlyAffectsRequester() throws Exception {
		member("me@test.local", "본인");
		member("other@test.local", "다른회원");

		for (int attempt = 0; attempt < 2; attempt++) {
			mvc.perform(change("me@test.local", "{\"isPrivate\":true}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.isPrivate").value(true));
		}
		assertTrue(stored("me@test.local"));
		assertFalse(stored("other@test.local"));
		mvc.perform(as("me@test.local", get(URL))).andExpect(jsonPath("$.isPrivate").value(true));
		mvc.perform(as("other@test.local", get(URL))).andExpect(jsonPath("$.isPrivate").value(false));

		// 요청 값의 사용자 ID는 쓰지 않고 토큰의 회원만 바꾼다.
		Long otherId = members.findByEmail("other@test.local").orElseThrow().getId();
		mvc.perform(change("me@test.local", "{\"isPrivate\":false,\"userId\":" + otherId + "}").param("userId", otherId.toString()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.isPrivate").value(false));
		mvc.perform(change("me@test.local", "{\"isPrivate\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.isPrivate").value(false));
		assertFalse(stored("me@test.local"));
		assertFalse(stored("other@test.local"));
	}

	@Test
	void changeKeepsOtherColumns() throws Exception {
		Member me = member("me@test.local", "본인");
		jdbc.update("UPDATE users SET summary = '한 줄 소개', contact_hours = '평일 저녁' WHERE id = ?", me.getId());
		mvc.perform(change("me@test.local", "{\"isPrivate\":true}")).andExpect(status().isOk());
		Map<String, Object> row = jdbc.queryForMap("SELECT nickname, summary, contact_hours, status FROM users WHERE id = ?", me.getId());
		assertEquals(Map.of("nickname", "본인", "summary", "한 줄 소개", "contact_hours", "평일 저녁", "status", "ACTIVE"), row);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "{}", "{\"isPrivate\":null}", "{\"isPrivate\":\"true\"}", "{\"isPrivate\":1}", "{\"isPrivate\":0}",
		"{\"isPrivate\":[true]}", "{\"private\":true}", "{\"locked\":true}", "{\"isPrivate\":tru", "true"})
	void rejectsBodiesThatAreNotABoolean(String body) throws Exception {
		member("me@test.local", "본인");
		mvc.perform(change("me@test.local", body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.message").isString())
			.andExpect(jsonPath("$.path").value(URL));
		assertFalse(stored("me@test.local"));
	}

	@Test
	void responseHasOnlyIsPrivateField() throws Exception {
		member("me@test.local", "본인");
		String read = mvc.perform(as("me@test.local", get(URL))).andReturn().getResponse().getContentAsString();
		String changed = mvc.perform(change("me@test.local", "{\"isPrivate\":true}")).andReturn().getResponse().getContentAsString();
		JsonMapper json = JsonMapper.builder().build();
		for (String body : List.of(read, changed)) {
			assertEquals(List.of("isPrivate"), List.copyOf(json.readTree(body).propertyNames()), body);
		}
	}

	@Test
	void listConditionKeepsPublicAccountsAndViewerOnly() {
		long publicId = member("public@test.local", "공개회원").getId();
		long privateId = member("private@test.local", "비공개회원").getId();
		long otherPrivateId = member("other-private@test.local", "다른비공개").getId();
		jdbc.update("UPDATE users SET is_private = TRUE WHERE id IN (?, ?)", privateId, otherPrivateId);

		assertEquals(List.of(publicId), visibleIds(null));
		assertEquals(List.of(publicId, privateId), visibleIds(privateId));
		assertEquals(List.of(publicId), visibleIds(publicId));
		// 없는 회원 ID가 와도 공개 계정만 남는다.
		assertEquals(List.of(publicId), visibleIds(Long.MAX_VALUE));
	}

	List<Long> visibleIds(Long viewerId) {
		var params = new MapSqlParameterSource(AccountVisibilityPolicy.VIEWER_PARAM, viewerId);
		return new NamedParameterJdbcTemplate(jdbc).queryForList(
			"SELECT u.id FROM users u WHERE " + AccountVisibilityPolicy.visibleAccountCondition("u", viewerId) + " ORDER BY u.id", params, Long.class);
	}

	@Test
	void suspendedMemberIsForbidden() throws Exception {
		member("suspended@test.local", "정지회원");
		suspended.suspend("suspended@test.local");
		try {
			mvc.perform(as("suspended@test.local", get(URL))).andExpect(status().isForbidden());
			mvc.perform(change("suspended@test.local", "{\"isPrivate\":true}")).andExpect(status().isForbidden());
			assertFalse(stored("suspended@test.local"));
		} finally {
			suspended.release("suspended@test.local");
		}
	}
}
