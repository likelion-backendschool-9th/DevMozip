package com.devmozip.backend.admin;

import static org.hamcrest.Matchers.*;
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

/** WBS 14.10 AI 사용량, 14.11 AI 오류 (docs/admin-plan.md 7단계, D6). 읽기만 하고 추천 조건은 응답에 넣지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AdminAiIntegrationTest {
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
		for (String table : List.of("ai_recommendation_requests", "ai_messages", "ai_conversations", "ai_usages", "users")) jdbc.update("DELETE FROM " + table);
		jdbc.update("INSERT INTO users(id, email, nickname, tag, role, status, is_private) VALUES (1, 'admin-ai@test.local', '운영자', 'admin', 'ADMIN', 'ACTIVE', false)");
		jdbc.update("INSERT INTO users(id, email, nickname, tag, role, status, is_private) VALUES (2, 'kim-ai@test.local', '김개발', 'kim', 'PREMIUM', 'ACTIVE', false)");
		jdbc.update("INSERT INTO users(id, nickname, is_private) VALUES (3, '탈퇴회원', false)");
		usage(2, THIS_MONTH, 4, 1);
		usage(3, THIS_MONTH, 7, 0);
		usage(2, "2026-01", 2, 0);
		jdbc.update("INSERT INTO ai_conversations(id, user_id, title, created_at, updated_at) VALUES (10, 2, '대화', localtimestamp, localtimestamp)");
		request(100, "SUCCESS");
		request(101, "FAILED");
		request(102, "FAILED");
		request(103, "PENDING");
	}

	void usage(long user, String month, int used, int pending) {
		jdbc.update("INSERT INTO ai_usages(user_id, usage_month, used_count, pending_count, created_at, updated_at) VALUES (?, ?, ?, ?, localtimestamp, localtimestamp)",
			user, month, used, pending);
	}

	void request(long id, String status) {
		jdbc.update("INSERT INTO ai_recommendation_requests(id, ai_conversation_id, conditions, idempotency_key, status, created_at, completed_at)"
			+ " VALUES (?, 10, '{\"secret\":\"조건\"}'::jsonb, ?, ?, localtimestamp, ?)", id, "key-" + id, status,
			"PENDING".equals(status) ? null : java.sql.Timestamp.valueOf("2026-11-10 10:00:00"));
	}

	MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
		return request.header("Authorization", "Bearer " + jwt.createAccessToken("admin-ai@test.local", "ADMIN"));
	}

	@Test
	void usageOfMonthDefaultsToThisMonthAndSortsByUse() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/admin/ai/usage"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.summary.month").value(THIS_MONTH))
			.andExpect(jsonPath("$.summary.totalUsed").value(11)).andExpect(jsonPath("$.summary.totalPending").value(1))
			.andExpect(jsonPath("$.summary.members").value(2)).andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.items[0].userId").value(3)).andExpect(jsonPath("$.items[0].withdrawn").value(true))
			.andExpect(jsonPath("$.items[0].tag").value(nullValue()))
			.andExpect(jsonPath("$.items[1].tag").value("kim")).andExpect(jsonPath("$.items[1].role").value("PREMIUM"))
			.andExpect(jsonPath("$.items[1].usedCount").value(4)).andExpect(jsonPath("$.items[1].pendingCount").value(1));
		mvc.perform(asAdmin(get("/api/v1/admin/ai/usage").param("month", "2026-01"))).andExpect(jsonPath("$.summary.totalUsed").value(2))
			.andExpect(jsonPath("$.items.length()").value(1));
		mvc.perform(asAdmin(get("/api/v1/admin/ai/usage").param("month", "2025-12"))).andExpect(jsonPath("$.totalElements").value(0))
			.andExpect(jsonPath("$.summary.totalUsed").value(0));
		mvc.perform(asAdmin(get("/api/v1/admin/ai/usage").param("size", "1").param("page", "1"))).andExpect(jsonPath("$.items[0].userId").value(2))
			.andExpect(jsonPath("$.totalPages").value(2));
		for (String month : List.of("2026-13", "202611", "11-2026")) {
			mvc.perform(asAdmin(get("/api/v1/admin/ai/usage").param("month", month))).andExpect(status().isBadRequest());
		}
	}

	@Test
	void logsShowOnlyFailuresWithoutConditions() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/admin/ai/logs"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.items[0].requestId").value(102)).andExpect(jsonPath("$.items[1].requestId").value(101))
			.andExpect(jsonPath("$.items[0].conversationId").value(10)).andExpect(jsonPath("$.items[0].userTag").value("kim"))
			.andExpect(jsonPath("$.items[0].status").value("FAILED")).andExpect(jsonPath("$.items[0].completedAt").isNotEmpty())
			.andExpect(jsonPath("$.items[0].conditions").doesNotExist())
			.andExpect(content().string(not(containsString("secret"))));
		mvc.perform(get("/api/v1/admin/ai/logs").header("Authorization", "Bearer " + jwt.createAccessToken("kim-ai@test.local", "PREMIUM")))
			.andExpect(status().isForbidden());
	}
}
