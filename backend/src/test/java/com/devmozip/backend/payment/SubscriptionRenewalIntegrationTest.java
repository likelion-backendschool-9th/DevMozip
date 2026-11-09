package com.devmozip.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.devmozip.backend.client.PortOneClient;
import com.devmozip.backend.client.PortOneClient.PaidPayment;
import com.devmozip.backend.client.PortOneException;
import com.devmozip.backend.config.PaymentProperties;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberRole;
import com.devmozip.backend.entity.Payment;
import com.devmozip.backend.entity.Subscription;
import com.devmozip.backend.entity.SubscriptionPlan;
import com.devmozip.backend.entity.enums.PaymentProvider;
import com.devmozip.backend.entity.enums.SubscriptionStatus;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.PaymentRepository;
import com.devmozip.backend.repository.SubscriptionPlanRepository;
import com.devmozip.backend.repository.SubscriptionRepository;
import com.devmozip.backend.scheduler.SubscriptionRenewalScheduler;
import com.devmozip.backend.security.jwt.InMemoryRefreshTokenStore;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.security.payment.PaymentMethodCodec;
import com.devmozip.backend.security.payment.StoredPaymentMethod;
import com.devmozip.backend.service.SubscriptionRenewalService;
import com.devmozip.backend.service.SubscriptionRenewalService.RenewalResult;

/** 매월 결제·해지 예약 만료를 H2와 가짜 포트원으로 확인한다. 실제 포트원 결제 검증이 아니다. */
@SpringBootTest
class SubscriptionRenewalIntegrationTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 9, 12, 5);

	@TestConfiguration
	static class Stores {
		@Bean @Primary RefreshTokenStore inMemoryRefreshTokenStore() { return new InMemoryRefreshTokenStore(); }
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		String secret = UUID.randomUUID().toString();
		registry.add("jwt.secret", () -> secret + secret);
		registry.add("spring.datasource.url", () -> "jdbc:h2:mem:subscription-renewal;MODE=PostgreSQL");
		registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
		registry.add("spring.datasource.username", () -> "sa");
		registry.add("spring.datasource.password", () -> "");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		registry.add("management.health.redis.enabled", () -> "false");
		registry.add("payment.store-id", () -> "store-test");
		registry.add("payment.api-secret", () -> "portone-secret");
		registry.add("payment.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
		registry.add("payment.renewal-enabled", () -> "false");
	}

	@Autowired SubscriptionRenewalService renewal;
	@Autowired MemberRepository members;
	@Autowired SubscriptionPlanRepository plans;
	@Autowired SubscriptionRepository subscriptions;
	@Autowired PaymentRepository payments;
	@Autowired PaymentMethodCodec codec;
	@Autowired PaymentProperties properties;
	@Autowired JdbcTemplate jdbc;
	@Autowired PlatformTransactionManager transactionManager;
	@Autowired ApplicationContext context;
	@MockitoBean PortOneClient portOne;

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM payments");
		jdbc.update("DELETE FROM subscriptions");
		when(portOne.payWithBillingKey(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString()))
				.thenReturn(new PaidPayment("pg-renew", "2026-12-09T03:05:00Z"));
	}

	/** 첫 결제를 마친 구독. 결제 수단을 저장하고 역할은 PREMIUM이다 */
	private Subscription subscribed(LocalDateTime startsAt, String billingKey) {
		return new TransactionTemplate(transactionManager).execute(status -> {
			String id = UUID.randomUUID().toString().substring(0, 12);
			Member member = members.save(Member.builder().email(id + "@renew.test").nickname("r" + id).role(MemberRole.USER)
					.status(Member.MemberStatus.ACTIVE).build());
			SubscriptionPlan plan = plans.findAllByActiveTrueOrderByIdAsc().getFirst();
			Subscription subscription = subscriptions.save(Subscription.start(member, plan, startsAt));
			payments.save(Payment.paid(subscription, UUID.randomUUID().toString(), PaymentProvider.TOSS_PAYMENTS, java.util.Map.of("display", "신한카드"),
					plan.getPrice(), "KRW", "pg-first", startsAt, startsAt));
			codec.store(member, new StoredPaymentMethod(PaymentProvider.TOSS_PAYMENTS, "customer-" + id, billingKey, "신한카드", startsAt));
			member.upgradeToPremium();
			return subscription;
		});
	}

	private String status(Subscription subscription) {
		return jdbc.queryForObject("SELECT status FROM subscriptions WHERE id = ?", String.class, subscription.getId());
	}

	private String role(Subscription subscription) {
		return jdbc.queryForObject("SELECT u.role FROM users u JOIN subscriptions s ON s.user_id = u.id WHERE s.id = ?", String.class, subscription.getId());
	}

	@Test
	void 기간이_끝난_구독을_매월_결제하고_기간을_한_달_늘린다() {
		Subscription due = subscribed(LocalDateTime.of(2026, 11, 9, 12, 0), "billing-key-due");
		Subscription notDue = subscribed(LocalDateTime.of(2026, 11, 20, 12, 0), "billing-key-later");
		assertThat(renewal.run(NOW)).isEqualTo(new RenewalResult(1, 0, 0, 0, 0));
		verify(portOne).payWithBillingKey(anyString(), eq("billing-key-due"), eq("DevMozip 프리미엄 월 구독"), anyString(), eq(9900L), eq("KRW"));
		verify(portOne, never()).payWithBillingKey(anyString(), eq("billing-key-later"), anyString(), anyString(), anyLong(), anyString());
		assertThat(status(due)).isEqualTo("ACTIVE");
		assertThat(jdbc.queryForObject("SELECT current_period_end FROM subscriptions WHERE id = ?", LocalDateTime.class, due.getId()))
				.isEqualTo(LocalDateTime.of(2027, 1, 9, 12, 0));
		assertThat(jdbc.queryForList("SELECT status FROM payments WHERE subscription_id = ? ORDER BY id", String.class, due.getId()))
				.containsExactly("PAID", "PAID");
		assertThat(role(due)).isEqualTo("PREMIUM");
		assertThat(status(notDue)).isEqualTo("ACTIVE");
		assertThat(renewal.run(NOW)).isEqualTo(new RenewalResult(0, 0, 0, 0, 0));
	}

	@Test
	void 말일에_시작한_구독은_시작일_기준으로_기간을_정한다() {
		Subscription subscription = subscribed(LocalDateTime.of(2027, 1, 31, 9, 0), "billing-key-month-end");
		renewal.run(LocalDateTime.of(2027, 2, 28, 9, 0));
		assertThat(jdbc.queryForObject("SELECT current_period_end FROM subscriptions WHERE id = ?", LocalDateTime.class, subscription.getId()))
				.isEqualTo(LocalDateTime.of(2027, 3, 31, 9, 0));
	}

	@Test
	void 결제가_실패하면_구독을_취소하고_빌링키를_삭제하며_무료로_바꾼다() {
		Subscription subscription = subscribed(LocalDateTime.of(2026, 11, 9, 12, 0), "billing-key-declined");
		when(portOne.payWithBillingKey(anyString(), eq("billing-key-declined"), anyString(), anyString(), anyLong(), anyString()))
				.thenThrow(new PortOneException(PortOneException.Kind.REJECTED, 502, "PG_PROVIDER"));
		assertThat(renewal.run(NOW)).isEqualTo(new RenewalResult(0, 1, 0, 0, 0));
		assertThat(status(subscription)).isEqualTo("CANCELED");
		assertThat(jdbc.queryForObject("SELECT ended_at FROM subscriptions WHERE id = ?", LocalDateTime.class, subscription.getId())).isEqualTo(NOW);
		assertThat(role(subscription)).isEqualTo("USER");
		assertThat(jdbc.queryForObject("SELECT u.payment_methods FROM users u JOIN subscriptions s ON s.user_id = u.id WHERE s.id = ?", String.class,
				subscription.getId())).isNull();
		assertThat(jdbc.queryForList("SELECT status FROM payments WHERE subscription_id = ? ORDER BY id", String.class, subscription.getId()))
				.containsExactly("PAID", "FAILED");
		verify(portOne).deleteBillingKey("billing-key-declined");
		assertThat(renewal.run(NOW.plusMonths(1))).isEqualTo(new RenewalResult(0, 0, 0, 0, 0));
	}

	@Test
	void 결과를_모르는_결제는_다시_결제하지_않고_RENEWING으로_남긴다() {
		Subscription subscription = subscribed(LocalDateTime.of(2026, 11, 9, 12, 0), "billing-key-timeout");
		when(portOne.payWithBillingKey(anyString(), eq("billing-key-timeout"), anyString(), anyString(), anyLong(), anyString()))
				.thenThrow(new PortOneException(PortOneException.Kind.UNKNOWN, 0, null));
		assertThat(renewal.run(NOW)).isEqualTo(new RenewalResult(0, 0, 1, 0, 0));
		assertThat(status(subscription)).isEqualTo("RENEWING");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE status = 'PENDING' AND created_at = ?", Long.class, NOW)).isEqualTo(1);
		assertThat(renewal.run(NOW.plusMinutes(10))).isEqualTo(new RenewalResult(0, 0, 0, 0, 0));
		verify(portOne, times(1)).payWithBillingKey(anyString(), eq("billing-key-timeout"), anyString(), anyString(), anyLong(), anyString());
		assertThat(role(subscription)).isEqualTo("PREMIUM");
	}

	@Test
	void 해지_예약_구독은_기간이_끝나면_결제_없이_만료한다() {
		Subscription subscription = subscribed(LocalDateTime.of(2026, 11, 9, 12, 0), "billing-key-cancelled");
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			Subscription managed = subscriptions.findWithMemberAndPlanById(subscription.getId()).orElseThrow();
			managed.requestCancel(LocalDateTime.of(2026, 11, 20, 0, 0));
			codec.clear(managed.getMember());
		});
		assertThat(renewal.run(NOW.minusDays(1))).isEqualTo(new RenewalResult(0, 0, 0, 0, 0));
		assertThat(renewal.run(NOW)).isEqualTo(new RenewalResult(0, 0, 0, 1, 0));
		assertThat(status(subscription)).isEqualTo("EXPIRED");
		assertThat(role(subscription)).isEqualTo("USER");
		verify(portOne, never()).payWithBillingKey(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());
	}

	@Test
	void 만료_때_역할이_PREMIUM이_아니면_건드리지_않는다() {
		Subscription subscription = subscribed(LocalDateTime.of(2026, 11, 9, 12, 0), "billing-key-admin");
		jdbc.update("UPDATE subscriptions SET status = 'CANCEL_REQUESTED' WHERE id = ?", subscription.getId());
		jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = (SELECT user_id FROM subscriptions WHERE id = ?)", subscription.getId());
		renewal.run(NOW);
		assertThat(role(subscription)).isEqualTo("ADMIN");
	}

	@Test
	void 결제_수단을_읽을_수_없거나_포트원_설정이_없으면_결제를_미룬다() {
		Subscription unreadable = subscribed(LocalDateTime.of(2026, 11, 9, 12, 0), "billing-key-broken");
		jdbc.update("UPDATE users SET payment_methods = NULL WHERE id = (SELECT user_id FROM subscriptions WHERE id = ?)", unreadable.getId());
		assertThat(renewal.run(NOW)).isEqualTo(new RenewalResult(0, 0, 0, 0, 1));
		assertThat(status(unreadable)).isEqualTo("ACTIVE");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE subscription_id = ?", Long.class, unreadable.getId())).isEqualTo(1);

		String secret = properties.getApiSecret();
		properties.setApiSecret("");
		try {
			assertThat(renewal.run(NOW)).isEqualTo(new RenewalResult(0, 0, 0, 0, 1));
		} finally {
			properties.setApiSecret(secret);
		}
		verify(portOne, never()).payWithBillingKey(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());
	}

	@Test
	void 스케줄러는_설정으로_끌_수_있다() {
		assertThat(context.getBeanNamesForType(SubscriptionRenewalScheduler.class)).isEmpty();
		assertThat(status(subscribed(LocalDateTime.of(2026, 11, 9, 12, 0), "billing-key-x"))).isEqualTo(SubscriptionStatus.ACTIVE.name());
	}
}
