package com.devmozip.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.devmozip.backend.client.PortOneClient;
import com.devmozip.backend.client.PortOneClient.BillingKeyInfo;
import com.devmozip.backend.client.PortOneClient.PaidPayment;
import com.devmozip.backend.client.PortOneException;
import com.devmozip.backend.config.PaymentProperties;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberRole;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.InMemoryRefreshTokenStore;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.service.PaymentCheckoutService;
import com.devmozip.backend.service.impl.InMemoryPaymentCheckoutService;
import com.jayway.jsonpath.JsonPath;

/**
 * 구독·결제 API를 실제 보안 설정·H2로 확인한다. 포트원 호출은 가짜이고 결제 대기 정보는 메모리 구현이므로
 * 실제 포트원·Redis 검증이 아니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentApiIntegrationTest {
	private static final String PASSWORD = "Devmozip123";
	private static final String TOSS_CHANNEL = "channel-key-toss-test";
	private static final String KAKAO_CHANNEL = "channel-key-kakao-test";
	private static final String BILLING_KEY = "billing-key-test-0001";

	@TestConfiguration
	static class Stores {
		@Bean @Primary RefreshTokenStore inMemoryRefreshTokenStore() { return new InMemoryRefreshTokenStore(); }
		@Bean @Primary PaymentCheckoutService inMemoryPaymentCheckoutService(PaymentProperties properties) { return new InMemoryPaymentCheckoutService(properties); }
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		String secret = UUID.randomUUID().toString();
		registry.add("jwt.secret", () -> secret + secret);
		registry.add("spring.datasource.url", () -> "jdbc:h2:mem:payment-api;MODE=PostgreSQL");
		registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
		registry.add("spring.datasource.username", () -> "sa");
		registry.add("spring.datasource.password", () -> "");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		registry.add("management.health.redis.enabled", () -> "false");
		registry.add("payment.store-id", () -> "store-test");
		registry.add("payment.api-secret", () -> "portone-secret");
		registry.add("payment.toss-channel-key", () -> TOSS_CHANNEL);
		registry.add("payment.kakaopay-channel-key", () -> KAKAO_CHANNEL);
		registry.add("payment.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
		registry.add("payment.domain-name", () -> "https://devmozip.test");
		registry.add("payment.renewal-enabled", () -> "false");
	}

	@Autowired MockMvc mvc;
	@Autowired MemberRepository members;
	@Autowired PasswordEncoder encoder;
	@Autowired JdbcTemplate jdbc;
	@Autowired PaymentProperties paymentProperties;
	@MockitoBean PortOneClient portOne;

	private long planId;

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM payments");
		jdbc.update("DELETE FROM subscriptions");
		planId = jdbc.queryForObject("SELECT id FROM subscription_plans WHERE is_active = true ORDER BY id LIMIT 1", Long.class);
		when(portOne.payWithBillingKey(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString()))
				.thenReturn(new PaidPayment("pg-tx-1", "2026-11-09T03:00:00Z"));
	}

	private String member(MemberRole role) {
		String email = UUID.randomUUID() + "@payment.test";
		members.saveAndFlush(Member.builder().email(email).passwordHash(encoder.encode(PASSWORD)).nickname(UUID.randomUUID().toString().substring(0, 20))
				.role(role).status(Member.MemberStatus.ACTIVE).build());
		return email;
	}

	private String loginBody(String email) throws Exception {
		return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private String token(String email) throws Exception {
		return "Bearer " + JsonPath.read(loginBody(email), "$.accessToken");
	}

	private ResultActions checkout(String token, String provider) throws Exception {
		return mvc.perform(post("/api/v1/payments/checkout").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"planId\":%d,\"provider\":\"%s\",\"amount\":1}".formatted(planId, provider)));
	}

	private String orderId(String token, String provider) throws Exception {
		return JsonPath.read(checkout(token, provider).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.orderId");
	}

	private ResultActions confirm(String token, String orderId, String billingKey) throws Exception {
		return mvc.perform(post("/api/v1/payments/confirm").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"orderId\":\"%s\",\"billingKey\":\"%s\"}".formatted(orderId, billingKey)));
	}

	private void issued(String orderId, String channel) {
		when(portOne.getBillingKey(BILLING_KEY)).thenReturn(new BillingKeyInfo("ISSUED", orderId, List.of(channel), "신한카드 1234****"));
	}

	private String subscribe(String email, String provider) throws Exception {
		String token = token(email);
		String orderId = orderId(token, provider);
		issued(orderId, provider.equals("KAKAO_PAY") ? KAKAO_CHANNEL : TOSS_CHANNEL);
		confirm(token, orderId, BILLING_KEY).andExpect(status().isOk());
		return orderId;
	}

	@Test
	void 상품_안내는_로그인_없이_볼_수_있다() throws Exception {
		mvc.perform(get("/api/v1/subscription/plans")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("프리미엄")).andExpect(jsonPath("$[0].price").value(9900))
				.andExpect(jsonPath("$[0].currency").value("KRW")).andExpect(jsonPath("$[0].billingCycle").value("MONTHLY"));
		mvc.perform(get("/api/v1/subscription/me")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/payments/checkout").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
	}

	@Test
	void 결제_준비는_포트원_SDK에_넘길_값을_주고_금액은_서버가_정한다() throws Exception {
		String token = token(member(MemberRole.USER));
		String body = checkout(token, "KAKAO_PAY").andExpect(status().isCreated())
				.andExpect(jsonPath("$.storeId").value("store-test")).andExpect(jsonPath("$.channelKey").value(KAKAO_CHANNEL))
				.andExpect(jsonPath("$.billingKeyMethod").value("EASY_PAY")).andExpect(jsonPath("$.issueName").value("DevMozip 프리미엄 월 구독"))
				.andReturn().getResponse().getContentAsString();
		String orderId = JsonPath.read(body, "$.orderId");
		String customerId = JsonPath.read(body, "$.customerId");
		assertThat((String) JsonPath.read(body, "$.redirectUrl")).isEqualTo("https://devmozip.test/api/payments/callback?orderId=" + orderId);
		assertThat(body).doesNotContain("portone-secret");
		issued(orderId, KAKAO_CHANNEL);
		confirm(token, orderId, BILLING_KEY).andExpect(status().isOk());
		verify(portOne).payWithBillingKey(orderId, BILLING_KEY, "DevMozip 프리미엄 월 구독", customerId, 9900, "KRW");
	}

	@Test
	void 결제_승인하면_구독이_시작되고_역할이_PREMIUM이_되며_재발급_토큰에_반영된다() throws Exception {
		String email = member(MemberRole.USER);
		// 회원당 리프레시 토큰은 하나만 유효하므로 한 번 로그인한 값을 함께 쓴다.
		String login = loginBody(email);
		String refreshToken = JsonPath.read(login, "$.refreshToken");
		String token = "Bearer " + JsonPath.read(login, "$.accessToken");
		String orderId = orderId(token, "TOSS_PAYMENTS");
		issued(orderId, TOSS_CHANNEL);
		confirm(token, orderId, BILLING_KEY).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.premium").value(true))
				.andExpect(jsonPath("$.startsAt").value("2026-11-09T12:00:00+09:00"))
				.andExpect(jsonPath("$.nextBillingAt").value("2026-12-09T12:00:00+09:00"))
				.andExpect(jsonPath("$.paymentMethod.display").value("신한카드 1234****"));

		assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email = ?", String.class, email)).isEqualTo("PREMIUM");
		String stored = jdbc.queryForObject("SELECT CAST(payment_methods AS VARCHAR) FROM users WHERE email = ?", String.class, email);
		assertThat(stored).contains("TOSS_PAYMENTS").doesNotContain(BILLING_KEY);
		assertThat(jdbc.queryForObject("SELECT order_id || ':' || status || ':' || provider_payment_id FROM payments", String.class))
				.isEqualTo(orderId + ":PAID:pg-tx-1");

		String refreshed = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + refreshToken + "\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + JsonPath.read(refreshed, "$.accessToken")))
				.andExpect(jsonPath("$.role").value("PREMIUM"));
		mvc.perform(get("/api/v1/subscription/me").header("Authorization", token)).andExpect(jsonPath("$.status").value("ACTIVE"));
		mvc.perform(get("/api/v1/payment-methods/me").header("Authorization", token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.provider").value("TOSS_PAYMENTS"));
	}

	@Test
	void 같은_주문을_두_번_승인하거나_다른_회원이_승인할_수_없다() throws Exception {
		String owner = token(member(MemberRole.USER));
		String orderId = orderId(owner, "TOSS_PAYMENTS");
		issued(orderId, TOSS_CHANNEL);
		confirm(token(member(MemberRole.USER)), orderId, BILLING_KEY).andExpect(status().isNotFound());
		confirm(owner, orderId, BILLING_KEY).andExpect(status().isNotFound());
		verify(portOne, never()).payWithBillingKey(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());

		String again = orderId(owner, "TOSS_PAYMENTS");
		issued(again, TOSS_CHANNEL);
		confirm(owner, again, BILLING_KEY).andExpect(status().isOk());
		confirm(owner, again, BILLING_KEY).andExpect(status().isNotFound());
	}

	@Test
	void 다른_주문으로_발급한_빌링키는_거절하고_삭제하지_않는다() throws Exception {
		String token = token(member(MemberRole.USER));
		String orderId = orderId(token, "TOSS_PAYMENTS");
		issued("someone-elses-order", TOSS_CHANNEL);
		confirm(token, orderId, BILLING_KEY).andExpect(status().isBadRequest());
		verify(portOne, never()).deleteBillingKey(BILLING_KEY);
		verify(portOne, never()).payWithBillingKey(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());
	}

	@Test
	void 다른_채널이거나_삭제된_빌링키는_거절하고_삭제한다() throws Exception {
		String token = token(member(MemberRole.USER));
		String orderId = orderId(token, "TOSS_PAYMENTS");
		issued(orderId, KAKAO_CHANNEL);
		confirm(token, orderId, BILLING_KEY).andExpect(status().isBadRequest());
		String deleted = orderId(token, "TOSS_PAYMENTS");
		when(portOne.getBillingKey(BILLING_KEY)).thenReturn(new BillingKeyInfo("DELETED", deleted, List.of(TOSS_CHANNEL), ""));
		confirm(token, deleted, BILLING_KEY).andExpect(status().isBadRequest());
		verify(portOne, org.mockito.Mockito.times(2)).deleteBillingKey(BILLING_KEY);
		verify(portOne, never()).payWithBillingKey(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());
	}

	@Test
	void 결제_대상이_아니면_결제를_시작할_수_없다() throws Exception {
		checkout(token(member(MemberRole.PREMIUM)), "TOSS_PAYMENTS").andExpect(status().isConflict());
		checkout(token(member(MemberRole.ADMIN)), "TOSS_PAYMENTS").andExpect(status().isForbidden());
		String email = member(MemberRole.USER);
		subscribe(email, "TOSS_PAYMENTS");
		checkout(token(email), "TOSS_PAYMENTS").andExpect(status().isConflict());
		mvc.perform(post("/api/v1/payments/checkout").header("Authorization", token(member(MemberRole.USER))).contentType(MediaType.APPLICATION_JSON)
				.content("{\"planId\":999,\"provider\":\"TOSS_PAYMENTS\"}")).andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/payments/checkout").header("Authorization", token(member(MemberRole.USER))).contentType(MediaType.APPLICATION_JSON)
				.content("{\"planId\":1,\"provider\":\"PAYPAL\"}")).andExpect(status().isBadRequest());
	}

	@Test
	void 채널_설정이_없는_결제_수단만_503이다() throws Exception {
		String token = token(member(MemberRole.USER));
		String original = paymentProperties.getKakaopayChannelKey();
		paymentProperties.setKakaopayChannelKey("");
		try {
			checkout(token, "KAKAO_PAY").andExpect(status().isServiceUnavailable());
			checkout(token, "TOSS_PAYMENTS").andExpect(status().isCreated());
		} finally {
			paymentProperties.setKakaopayChannelKey(original);
		}
	}

	@Test
	void 첫_결제가_거절되면_빌링키를_삭제하고_구독을_만들지_않는다() throws Exception {
		String email = member(MemberRole.USER);
		String token = token(email);
		String orderId = orderId(token, "TOSS_PAYMENTS");
		issued(orderId, TOSS_CHANNEL);
		when(portOne.payWithBillingKey(eq(orderId), anyString(), anyString(), anyString(), anyLong(), anyString()))
				.thenThrow(new PortOneException(PortOneException.Kind.REJECTED, 502, "PG_PROVIDER"));
		confirm(token, orderId, BILLING_KEY).andExpect(status().isBadRequest());
		verify(portOne).deleteBillingKey(BILLING_KEY);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscriptions", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email = ?", String.class, email)).isEqualTo("USER");
	}

	@Test
	void 결제_결과를_모르면_취소를_시도하고_빌링키를_삭제한다() throws Exception {
		String token = token(member(MemberRole.USER));
		String orderId = orderId(token, "KAKAO_PAY");
		issued(orderId, KAKAO_CHANNEL);
		when(portOne.payWithBillingKey(eq(orderId), anyString(), anyString(), anyString(), anyLong(), anyString()))
				.thenThrow(new PortOneException(PortOneException.Kind.UNKNOWN, 0, null));
		confirm(token, orderId, BILLING_KEY).andExpect(status().isBadGateway());
		verify(portOne).cancelPayment(orderId, "결제 결과 확인 실패");
		verify(portOne).deleteBillingKey(BILLING_KEY);
	}

	@Test
	void 결제_후_다른_결제가_먼저_끝났으면_이번_결제를_취소한다() throws Exception {
		String email = member(MemberRole.USER);
		String token = token(email);
		String orderId = orderId(token, "TOSS_PAYMENTS");
		issued(orderId, TOSS_CHANNEL);
		doAnswer(invocation -> {
			jdbc.update("UPDATE users SET role = 'PREMIUM' WHERE email = ?", email);
			return new PaidPayment("pg-tx-2", "2026-11-09T03:00:00Z");
		}).when(portOne).payWithBillingKey(eq(orderId), anyString(), anyString(), anyString(), anyLong(), anyString());
		confirm(token, orderId, BILLING_KEY).andExpect(status().isConflict());
		verify(portOne).cancelPayment(orderId, "구독 저장 실패");
		verify(portOne).deleteBillingKey(BILLING_KEY);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments", Long.class)).isZero();
	}

	@Test
	void 해지하면_빌링키를_삭제하고_기간_끝까지_유지하며_다시_해지할_수_없다() throws Exception {
		String email = member(MemberRole.USER);
		subscribe(email, "KAKAO_PAY");
		String token = token(email);
		mvc.perform(post("/api/v1/subscription/cancel").header("Authorization", token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCEL_REQUESTED")).andExpect(jsonPath("$.premium").value(true))
				.andExpect(jsonPath("$.nextBillingAt").doesNotExist()).andExpect(jsonPath("$.currentPeriodEnd").exists());
		verify(portOne).deleteBillingKey(BILLING_KEY);
		assertThat(jdbc.queryForObject("SELECT payment_methods FROM users WHERE email = ?", String.class, email)).isNull();
		assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email = ?", String.class, email)).isEqualTo("PREMIUM");
		mvc.perform(post("/api/v1/subscription/cancel").header("Authorization", token)).andExpect(status().isConflict());
		mvc.perform(get("/api/v1/payment-methods/me").header("Authorization", token)).andExpect(status().isNoContent());
		mvc.perform(post("/api/v1/subscription/cancel").header("Authorization", token(member(MemberRole.USER)))).andExpect(status().isConflict());
	}

	@Test
	void 해지_후_빌링키_삭제가_실패해도_해지는_유지된다() throws Exception {
		String email = member(MemberRole.USER);
		subscribe(email, "TOSS_PAYMENTS");
		doAnswer(invocation -> { throw new PortOneException(PortOneException.Kind.UNKNOWN, 0, null); }).when(portOne).deleteBillingKey(any());
		mvc.perform(post("/api/v1/subscription/cancel").header("Authorization", token(email))).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCEL_REQUESTED"));
	}

	@Test
	void 결제_내역은_본인_것만_최신순으로_페이지를_나눠_보여_준다() throws Exception {
		String email = member(MemberRole.USER);
		subscribe(email, "TOSS_PAYMENTS");
		subscribe(member(MemberRole.USER), "KAKAO_PAY");
		String token = token(email);
		mvc.perform(get("/api/v1/payments/me").header("Authorization", token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].amount").value(9900))
				.andExpect(jsonPath("$.items[0].status").value("PAID")).andExpect(jsonPath("$.items[0].planName").value("프리미엄"))
				.andExpect(jsonPath("$.items[0].paymentMethod").value("신한카드 1234****"))
				.andExpect(jsonPath("$.items[0].occurredAt").value("2026-11-09T12:00:00+09:00"))
				.andExpect(jsonPath("$.hasNext").value(false)).andExpect(jsonPath("$.size").value(20));
		mvc.perform(get("/api/v1/payments/me?size=0").header("Authorization", token)).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/payments/me?size=101").header("Authorization", token)).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/payments/me?page=-1").header("Authorization", token)).andExpect(status().isBadRequest());
	}

	@Test
	void 구독한_적이_없으면_NONE이고_결제_기록_없는_PREMIUM은_premium만_true다() throws Exception {
		mvc.perform(get("/api/v1/subscription/me").header("Authorization", token(member(MemberRole.USER))))
				.andExpect(jsonPath("$.status").value("NONE")).andExpect(jsonPath("$.premium").value(false));
		mvc.perform(get("/api/v1/subscription/me").header("Authorization", token(member(MemberRole.PREMIUM))))
				.andExpect(jsonPath("$.status").value("NONE")).andExpect(jsonPath("$.premium").value(true));
	}
}
