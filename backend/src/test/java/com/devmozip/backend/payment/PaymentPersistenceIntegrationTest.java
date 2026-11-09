package com.devmozip.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Payment;
import com.devmozip.backend.entity.Subscription;
import com.devmozip.backend.entity.SubscriptionPlan;
import com.devmozip.backend.entity.enums.PaymentProvider;
import com.devmozip.backend.entity.enums.PaymentStatus;
import com.devmozip.backend.entity.enums.SubscriptionStatus;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.PaymentRepository;
import com.devmozip.backend.repository.SubscriptionPlanRepository;
import com.devmozip.backend.repository.SubscriptionRepository;

/**
 * 구독·결제 엔티티를 운영과 같은 PostgreSQL 17에 매핑해 확인한다.
 * Docker를 찾지 못하면 건너뛰므로 그 경우 PostgreSQL 검증은 미실행이다.
 */
@SpringBootTest(properties = "payment.renewal-enabled=false")
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PaymentPersistenceIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	@Autowired MemberRepository members;
	@Autowired SubscriptionPlanRepository plans;
	@Autowired SubscriptionRepository subscriptions;
	@Autowired PaymentRepository payments;
	@Autowired JdbcTemplate jdbc;
	@Autowired PlatformTransactionManager transactionManager;

	private SubscriptionPlan plan;

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM payments");
		jdbc.update("DELETE FROM subscriptions");
		plan = plans.findAllByActiveTrueOrderByIdAsc().getFirst();
	}

	private Member member() {
		String id = UUID.randomUUID().toString().substring(0, 12);
		return members.saveAndFlush(Member.builder().email(id + "@payment.test").nickname("n" + id)
				.role(Member.MemberRole.USER).status(Member.MemberStatus.ACTIVE).build());
	}

	private Subscription subscription(Member member, LocalDateTime startsAt) {
		return subscriptions.saveAndFlush(Subscription.start(member, plan, startsAt));
	}

	private Payment paid(Subscription subscription, LocalDateTime at) {
		return payments.saveAndFlush(Payment.paid(subscription, UUID.randomUUID().toString(), PaymentProvider.KAKAO_PAY,
				Map.of("provider", "KAKAO_PAY", "display", "카카오페이"), plan.getPrice(), "KRW", "pg-tx", at, at));
	}

	@Test
	void 서버가_처음_뜨면_프리미엄_월_상품이_하나_들어간다() {
		assertThat(plans.findAllByActiveTrueOrderByIdAsc()).singleElement().satisfies(value -> {
			assertThat(value.getName()).isEqualTo("프리미엄");
			assertThat(value.getPrice()).isEqualByComparingTo("9900");
			assertThat(value.amountInWon()).isEqualTo(9900);
			assertThat(value.getCurrency()).isEqualTo("KRW");
			assertThat(value.getBillingCycle()).isEqualTo("MONTHLY");
			assertThat(value.getFeatureLimits()).containsEntry("aiRecommendationsPerMonth", 100);
		});
	}

	@Test
	void ERD_컬럼과_타입으로_테이블이_만들어진다() {
		assertThat(columns("subscription_plans")).containsExactlyInAnyOrder("id", "name", "price", "currency", "billing_cycle", "feature_limits", "is_active");
		assertThat(columns("subscriptions")).containsExactlyInAnyOrder("id", "user_id", "plan_id", "status", "starts_at", "current_period_end", "cancel_requested_at", "ended_at");
		assertThat(columns("payments")).containsExactlyInAnyOrder("id", "subscription_id", "order_id", "provider", "provider_payment_id", "payment_method_snapshot",
				"amount", "currency", "status", "paid_at", "created_at");
		assertThat(type("payments", "currency")).isEqualTo("character");
		assertThat(type("payments", "amount")).isEqualTo("numeric");
		assertThat(type("payments", "payment_method_snapshot")).isEqualTo("jsonb");
		assertThat(type("users", "payment_methods")).isEqualTo("jsonb");
		assertThat(jdbc.queryForList("""
				select ccu.table_name || '.' || ccu.column_name from information_schema.table_constraints tc
				join information_schema.key_column_usage kcu on tc.constraint_name = kcu.constraint_name
				join information_schema.constraint_column_usage ccu on tc.constraint_name = ccu.constraint_name
				where tc.constraint_type = 'FOREIGN KEY' and tc.table_name = 'subscriptions' and kcu.column_name = 'user_id'
				""", String.class)).containsExactly("users.id");
	}

	@Test
	void 구독과_결제를_저장하고_회원별_최신순으로_읽는다() {
		Member mine = member();
		Member other = member();
		Subscription subscription = subscription(mine, LocalDateTime.of(2026, 1, 31, 10, 0));
		assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(LocalDateTime.of(2026, 2, 28, 10, 0));
		assertThat(subscription.periodEndAfter(2)).isEqualTo(LocalDateTime.of(2026, 3, 31, 10, 0));
		Payment first = paid(subscription, LocalDateTime.of(2026, 1, 31, 10, 0));
		Payment second = paid(subscription, LocalDateTime.of(2026, 2, 28, 10, 0));
		paid(subscription(other, LocalDateTime.of(2026, 1, 1, 0, 0)), LocalDateTime.of(2026, 1, 1, 0, 0));

		var history = payments.findHistory(mine.getId(), PageRequest.of(0, 10));
		assertThat(history.getContent()).extracting(Payment::getId).containsExactly(second.getId(), first.getId());
		assertThat(history.getContent().getFirst().getPaymentMethodSnapshot()).containsEntry("display", "카카오페이");
		assertThat(history.getContent().getFirst().getSubscription().getPlan().getName()).isEqualTo("프리미엄");
		assertThat(payments.countBySubscriptionIdAndStatus(subscription.getId(), PaymentStatus.PAID)).isEqualTo(2);
		assertThat(subscriptions.findFirstByMemberIdAndStatusIn(mine.getId(), SubscriptionStatus.CURRENT)).isPresent();
	}

	@Test
	void 주문_번호는_중복될_수_없다() {
		Subscription subscription = subscription(member(), LocalDateTime.now());
		Payment saved = paid(subscription, LocalDateTime.now());
		assertThatThrownBy(() -> payments.saveAndFlush(Payment.pending(subscription, saved.getOrderId(), PaymentProvider.KAKAO_PAY, Map.of(),
				plan.getPrice(), "KRW", LocalDateTime.now()))).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 기간이_끝난_구독만_고르고_동시에_잡아도_한_실행만_성공한다() throws Exception {
		LocalDateTime now = LocalDateTime.of(2026, 11, 9, 12, 0);
		Subscription due = subscription(member(), now.minusMonths(1).minusMinutes(1));
		subscription(member(), now.minusDays(3));
		assertThat(subscriptions.findDueIds(SubscriptionStatus.ACTIVE, now)).containsExactly(due.getId());

		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			List<Callable<Integer>> tasks = new ArrayList<>();
			for (int i = 0; i < 4; i++) tasks.add(() -> {
				start.await();
				return tx.execute(status -> subscriptions.changeStatusIfDue(due.getId(), SubscriptionStatus.ACTIVE, SubscriptionStatus.RENEWING, now));
			});
			List<Future<Integer>> results = tasks.stream().map(pool::submit).toList();
			start.countDown();
			int claimed = 0;
			for (Future<Integer> result : results) claimed += result.get();
			assertThat(claimed).isEqualTo(1);
		} finally {
			pool.shutdownNow();
		}
		assertThat(subscriptions.findById(due.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.RENEWING);
		assertThat(subscriptions.findDueIds(SubscriptionStatus.ACTIVE, now)).isEmpty();
		// 결제를 마치고 기간이 늘어난 구독은 ACTIVE여도 다시 잡히지 않는다.
		Subscription later = subscription(member(), now.minusDays(3));
		Integer reclaimed = tx.execute(status -> subscriptions.changeStatusIfDue(later.getId(), SubscriptionStatus.ACTIVE, SubscriptionStatus.RENEWING, now));
		assertThat(reclaimed).isZero();
	}

	private List<String> columns(String table) {
		return jdbc.queryForList("select column_name from information_schema.columns where table_name = ?", String.class, table);
	}

	private String type(String table, String column) {
		return jdbc.queryForObject("select data_type from information_schema.columns where table_name = ? and column_name = ?", String.class, table, column);
	}
}
