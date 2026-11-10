package com.devmozip.backend.admin;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import com.devmozip.backend.client.PortOneClient;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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

/**
 * WBS 14.12 구독 관리, 14.13 결제 관리 (docs/admin-plan.md 8단계). 조회만 하며 포트원을 부르지 않는다.
 * 빌링키와 결제 수단 원문은 응답에 넣지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AdminBillingIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@MockitoBean PortOneClient portOne;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.sql.init.mode", () -> "never");
	}

	@BeforeEach
	void fixtures() {
		for (String table : List.of("payments", "subscriptions", "subscription_plans", "users")) jdbc.update("DELETE FROM " + table);
		jdbc.update("INSERT INTO users(id, email, nickname, tag, role, status, is_private) VALUES (1, 'admin-bill@test.local', '운영자', 'admin', 'ADMIN', 'ACTIVE', false)");
		jdbc.update("INSERT INTO users(id, email, nickname, tag, role, status, is_private, payment_methods) VALUES (2, 'kim-bill@test.local', '김개발', 'kim', 'PREMIUM', 'ACTIVE', false, '{\"billingKey\":\"encrypted-secret\"}'::jsonb)");
		jdbc.update("INSERT INTO users(id, nickname, is_private) VALUES (3, '탈퇴회원', false)");
		jdbc.update("INSERT INTO subscription_plans(id, name, price, currency, billing_cycle, is_active) VALUES (1, '프리미엄 월간', 9900, 'KRW', 'MONTHLY', true)");
		jdbc.update("INSERT INTO subscriptions(id, user_id, plan_id, status, starts_at, current_period_end) VALUES (10, 2, 1, 'ACTIVE', TIMESTAMP '2026-11-01 00:00:00', TIMESTAMP '2026-12-01 00:00:00')");
		jdbc.update("INSERT INTO subscriptions(id, user_id, plan_id, status, starts_at, current_period_end, cancel_requested_at, ended_at) VALUES"
			+ " (11, 3, 1, 'CANCELED', TIMESTAMP '2026-10-01 00:00:00', TIMESTAMP '2026-11-01 00:00:00', TIMESTAMP '2026-10-20 00:00:00', TIMESTAMP '2026-10-20 00:00:00')");
		payment(100, 10, "order-100", "PAID", "9900", "{\"provider\":\"TOSS_PAYMENTS\",\"display\":\"신한카드 1234\"}");
		payment(101, 11, "order-101", "PAID", "9900", "{\"provider\":\"KAKAO_PAY\",\"display\":\"카카오페이\"}");
		payment(102, 10, "order-102", "FAILED", "9900", null);
	}

	void payment(long id, long subscription, String order, String status, String amount, String snapshot) {
		jdbc.update("INSERT INTO payments(id, subscription_id, order_id, provider, payment_method_snapshot, amount, currency, status, paid_at, created_at)"
			+ " VALUES (?, ?, ?, 'TOSS_PAYMENTS', ?::jsonb, ?::numeric, 'KRW', ?, ?, TIMESTAMP '2026-11-01 00:00:00')",
			id, subscription, order, snapshot, amount, status, "PAID".equals(status) ? java.sql.Timestamp.valueOf("2026-11-01 00:00:01") : null);
	}

	MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
		return request.header("Authorization", "Bearer " + jwt.createAccessToken("admin-bill@test.local", "ADMIN"));
	}

	@Test
	void listsSubscriptionsWithStatusSummaryIncludingWithdrawal() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/admin/subscriptions"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.summary.current").value(1))
			.andExpect(jsonPath("$.summary.statusCounts.ACTIVE").value(1))
			.andExpect(jsonPath("$.summary.statusCounts.CANCELED").value(1))
			.andExpect(jsonPath("$.summary.statusCounts.EXPIRED").value(0))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.items[0].id").value(11)).andExpect(jsonPath("$.items[0].member.withdrawn").value(true))
			.andExpect(jsonPath("$.items[1].member.tag").value("kim")).andExpect(jsonPath("$.items[1].planName").value("프리미엄 월간"))
			.andExpect(content().string(not(containsString("encrypted-secret"))));
		mvc.perform(asAdmin(get("/api/v1/admin/subscriptions").param("status", "ACTIVE"))).andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(asAdmin(get("/api/v1/admin/subscriptions").param("status", "PAUSED"))).andExpect(status().isBadRequest());
	}

	@Test
	void listsPaymentsWithPaidTotalAndWithoutBillingKey() throws Exception {
		mvc.perform(asAdmin(get("/api/v1/admin/payments"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.summary.paidTotals[0].currency").value("KRW"))
			.andExpect(jsonPath("$.summary.paidTotals[0].amount").value(19800.0))
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.items[0].id").value(102)).andExpect(jsonPath("$.items[0].method").value(nullValue()))
			.andExpect(jsonPath("$.items[1].member.withdrawn").value(true)).andExpect(jsonPath("$.items[1].method").value("카카오페이"))
			.andExpect(jsonPath("$.items[2].method").value("신한카드 1234")).andExpect(jsonPath("$.items[2].orderId").value("order-100"))
			.andExpect(content().string(not(containsString("encrypted-secret"))))
			.andExpect(content().string(not(containsString("billingKey"))));
		mvc.perform(asAdmin(get("/api/v1/admin/payments").param("status", "FAILED"))).andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(asAdmin(get("/api/v1/admin/payments").param("status", "REFUNDED"))).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/admin/payments").header("Authorization", "Bearer " + jwt.createAccessToken("kim-bill@test.local", "PREMIUM")))
			.andExpect(status().isForbidden());
		Mockito.verifyNoInteractions(portOne);
	}
}
