package com.devmozip.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.devmozip.backend.client.PortOneClient;
import com.devmozip.backend.client.PortOneClient.PaidPayment;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Payment;
import com.devmozip.backend.entity.Subscription;
import com.devmozip.backend.entity.SubscriptionPlan;
import com.devmozip.backend.entity.enums.PaymentProvider;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.PaymentRepository;
import com.devmozip.backend.repository.SubscriptionPlanRepository;
import com.devmozip.backend.repository.SubscriptionRepository;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.security.payment.PaymentMethodCodec;
import com.devmozip.backend.security.payment.StoredPaymentMethod;
import com.devmozip.backend.service.SubscriptionRenewalService;

/**
 * 블루그린 배포 중처럼 매월 결제 실행이 겹쳐도 한 구독을 한 번만 결제하는지 PostgreSQL 17에서 확인한다.
 * 포트원은 가짜다. Docker가 없으면 건너뛰므로 그 경우 미실행이다.
 */
// 암호화 키는 32바이트 0을 base64로 만든 테스트 전용 값이다.
@SpringBootTest(properties = { "payment.renewal-enabled=false", "payment.store-id=store-test", "payment.api-secret=portone-secret",
		"payment.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" })
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SubscriptionRenewalConcurrencyIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	@Autowired SubscriptionRenewalService renewal;
	@Autowired MemberRepository members;
	@Autowired SubscriptionPlanRepository plans;
	@Autowired SubscriptionRepository subscriptions;
	@Autowired PaymentRepository payments;
	@Autowired PaymentMethodCodec codec;
	@Autowired PlatformTransactionManager transactionManager;
	@MockitoBean PortOneClient portOne;
	@MockitoBean RefreshTokenStore refreshTokens;

	@Test
	void 동시에_실행해도_구독마다_한_번만_결제한다() throws Exception {
		LocalDateTime startsAt = LocalDateTime.of(2026, 11, 9, 12, 0);
		int count = 5;
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			SubscriptionPlan plan = plans.findAllByActiveTrueOrderByIdAsc().getFirst();
			for (int i = 0; i < count; i++) {
				String id = UUID.randomUUID().toString().substring(0, 12);
				Member member = members.save(Member.builder().email(id + "@concurrent.test").nickname("c" + id).role(Member.MemberRole.USER)
						.status(Member.MemberStatus.ACTIVE).build());
				Subscription subscription = subscriptions.save(Subscription.start(member, plan, startsAt));
				payments.save(Payment.paid(subscription, UUID.randomUUID().toString(), PaymentProvider.KAKAO_PAY, Map.of("display", "카카오페이"),
						plan.getPrice(), "KRW", "pg-first", startsAt, startsAt));
				codec.store(member, new StoredPaymentMethod(PaymentProvider.KAKAO_PAY, "customer-" + id, "billing-key-" + i, "카카오페이", startsAt));
				member.upgradeToPremium();
			}
		});
		AtomicInteger charges = new AtomicInteger();
		when(portOne.payWithBillingKey(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString())).thenAnswer(invocation -> {
			charges.incrementAndGet();
			Thread.sleep(150);
			return new PaidPayment("pg-" + invocation.getArgument(0), "2026-12-09T03:00:00Z");
		});

		LocalDateTime now = LocalDateTime.of(2026, 12, 9, 12, 5);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(3);
		try {
			List<Callable<SubscriptionRenewalService.RenewalResult>> runs = List.of(() -> { start.await(); return renewal.run(now); },
					() -> { start.await(); return renewal.run(now); }, () -> { start.await(); return renewal.run(now); });
			List<Future<SubscriptionRenewalService.RenewalResult>> results = runs.stream().map(pool::submit).toList();
			start.countDown();
			int renewed = 0;
			for (Future<SubscriptionRenewalService.RenewalResult> result : results) renewed += result.get().renewed();
			assertThat(renewed).isEqualTo(count);
		} finally {
			pool.shutdownNow();
		}
		assertThat(charges.get()).isEqualTo(count);
		verify(portOne, atLeastOnce()).payWithBillingKey(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());
		assertThat(payments.findAll().stream().filter(payment -> payment.getStatus().name().equals("PAID")).count()).isEqualTo(count * 2L);
		assertThat(subscriptions.findAll()).allSatisfy(subscription -> {
			assertThat(subscription.getStatus().name()).isEqualTo("ACTIVE");
			assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(startsAt.plusMonths(2));
		});
	}
}
