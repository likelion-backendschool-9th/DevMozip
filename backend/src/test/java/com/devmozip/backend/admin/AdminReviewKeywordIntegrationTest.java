package com.devmozip.backend.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
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

/** WBS 14.9 리뷰 키워드 관리 (docs/admin-plan.md 6단계). 삭제는 비활성이고 행은 남는다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AdminReviewKeywordIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;

	static final String KEYWORDS = "/api/v1/admin/review-keywords";

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.sql.init.mode", () -> "never");
	}

	@BeforeEach
	void fixtures() {
		jdbc.update("DELETE FROM review_keyword_selections");
		jdbc.update("DELETE FROM review_keywords");
		jdbc.update("DELETE FROM users");
		jdbc.update("INSERT INTO users(id, email, nickname, tag, role, status, is_private) VALUES (1, 'admin-kw@test.local', '운영자', 'admin', 'ADMIN', 'ACTIVE', false)");
		jdbc.update("INSERT INTO users(id, email, nickname, tag, role, status, is_private) VALUES (2, 'user-kw@test.local', '회원', 'user', 'USER', 'ACTIVE', false)");
		// ID는 IDENTITY에 맡겨 서비스가 새 키워드를 넣을 때 기본 키가 겹치지 않게 한다. 넣은 순서가 ID 순서다.
		keyword("소통이 원활해요", true);
		keyword("약속을 잘 지켜요", true);
		keyword("함께 성장해요", false);
	}

	void keyword(String name, boolean active) {
		jdbc.update("INSERT INTO review_keywords(name, is_active, created_at, updated_at) VALUES (?, ?, localtimestamp, localtimestamp)", name, active);
	}

	MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
		return request.header("Authorization", "Bearer " + jwt.createAccessToken("admin-kw@test.local", "ADMIN"));
	}

	MockHttpServletRequestBuilder replace(String body) {
		return asAdmin(put(KEYWORDS).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	List<Map<String, Object>> rows() {
		return jdbc.queryForList("SELECT name, is_active FROM review_keywords ORDER BY id");
	}

	@Test
	void listsActiveAndInactiveInIdOrder() throws Exception {
		mvc.perform(asAdmin(get(KEYWORDS))).andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.items[0].name").value("소통이 원활해요")).andExpect(jsonPath("$.items[0].active").value(true))
			.andExpect(jsonPath("$.items[2].name").value("함께 성장해요")).andExpect(jsonPath("$.items[2].active").value(false));
		mvc.perform(get(KEYWORDS).header("Authorization", "Bearer " + jwt.createAccessToken("user-kw@test.local", "USER"))).andExpect(status().isForbidden());
	}

	@Test
	void replacesActiveSetWithoutDeletingRows() throws Exception {
		mvc.perform(replace("{\"keywords\":[\" 소통이 원활해요 \",\"함께 성장해요\",\"코드 리뷰가 꼼꼼해요\"]}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(4))
			.andExpect(jsonPath("$.items[1].active").value(false))
			.andExpect(jsonPath("$.items[2].active").value(true))
			.andExpect(jsonPath("$.items[3].name").value("코드 리뷰가 꼼꼼해요"));
		assertEquals(List.of(true, false, true, true), rows().stream().map(r -> r.get("is_active")).toList());
		// 같은 목록을 다시 보내도 결과가 같다.
		mvc.perform(replace("{\"keywords\":[\"소통이 원활해요\",\"함께 성장해요\",\"코드 리뷰가 꼼꼼해요\"]}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(4));
		// 빈 목록이면 모두 비활성이다. 행은 남는다.
		mvc.perform(replace("{\"keywords\":[]}")).andExpect(status().isOk());
		assertEquals(4, rows().size());
		assertTrue(rows().stream().noneMatch(r -> (Boolean) r.get("is_active")));
	}

	@Test
	void rejectsInvalidNamesWithoutChanges() throws Exception {
		for (String body : List.of("{\"keywords\":[\"  \"]}", "{\"keywords\":[\"" + "가".repeat(31) + "\"]}", "{\"keywords\":[\"소통이 원활해요\",\"소통이 원활해요\"]}",
			"{\"keywords\":[1]}", "{}", "{\"keywords\":\"소통\"}")) {
			mvc.perform(replace(body)).andExpect(status().isBadRequest());
		}
		assertEquals(List.of(true, true, false), rows().stream().map(r -> r.get("is_active")).toList());
	}
}
