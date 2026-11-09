package com.devmozip.backend.service.impl;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.devmozip.backend.client.PortOneClient;
import com.devmozip.backend.client.PortOneClient.PaidPayment;
import com.devmozip.backend.client.PortOneException;
import com.devmozip.backend.config.PaymentProperties;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Payment;
import com.devmozip.backend.entity.Subscription;
import com.devmozip.backend.entity.SubscriptionPlan;
import com.devmozip.backend.entity.enums.PaymentProvider;
import com.devmozip.backend.entity.enums.PaymentStatus;
import com.devmozip.backend.entity.enums.SubscriptionStatus;
import com.devmozip.backend.repository.PaymentRepository;
import com.devmozip.backend.repository.SubscriptionRepository;
import com.devmozip.backend.security.payment.PaymentMethodCodec;
import com.devmozip.backend.security.payment.StoredPaymentMethod;
import com.devmozip.backend.service.SubscriptionRenewalService;

/**
 * 구독을 잡을 때는 상태 조건부 UPDATE(ACTIVE → RENEWING)로 한 실행만 성공하게 하고, 결제 행(PENDING)과 함께 먼저 커밋한다.
 * 포트원을 부르는 동안에는 DB 잠금을 잡지 않는다. 결과를 모르는 결제는 다시 결제하지 않고 RENEWING으로 남긴다.
 */
@Service
public class SubscriptionRenewalServiceImpl implements SubscriptionRenewalService {
	private static final Logger log = LoggerFactory.getLogger(SubscriptionRenewalServiceImpl.class);
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final SubscriptionRepository subscriptions;
	private final PaymentRepository payments;
	private final PaymentMethodCodec paymentMethods;
	private final PortOneClient portOne;
	private final PaymentProperties properties;
	private final TransactionTemplate transaction;

	public SubscriptionRenewalServiceImpl(SubscriptionRepository subscriptions, PaymentRepository payments, PaymentMethodCodec paymentMethods,
			PortOneClient portOne, PaymentProperties properties, PlatformTransactionManager transactionManager) {
		this.subscriptions = subscriptions;
		this.payments = payments;
		this.paymentMethods = paymentMethods;
		this.portOne = portOne;
		this.properties = properties;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/** 잡은 구독의 결제 요청 정보. 빌링키는 이 객체 밖으로 내보내지 않는다 */
	private record Claim(long subscriptionId, long paymentId, String orderId, StoredPaymentMethod method, String orderName, long amount, String currency) {
	}

	private enum Outcome { RENEWED, FAILED, UNKNOWN, NOT_CLAIMED, SKIPPED }

	@Override
	public RenewalResult run(LocalDateTime now) {
		int expired = 0;
		for (Long id : subscriptions.findDueIds(SubscriptionStatus.CANCEL_REQUESTED, now)) {
			if (Boolean.TRUE.equals(transaction.execute(status -> expire(id, now)))) expired++;
		}
		int renewed = 0, failed = 0, unknown = 0, skipped = 0;
		if (!properties.isApiConfigured()) {
			// 설정이 없으면 결제할 수 없다. 구독은 ACTIVE로 두어 설정 후 다음 실행에서 결제한다.
			int waiting = subscriptions.findDueIds(SubscriptionStatus.ACTIVE, now).size();
			if (waiting > 0) log.warn("포트원 설정이 없어 매월 결제 {}건을 미룹니다.", waiting);
			return new RenewalResult(0, 0, 0, expired, waiting);
		}
		for (Long id : subscriptions.findDueIds(SubscriptionStatus.ACTIVE, now)) {
			switch (renew(id, now)) {
				case RENEWED -> renewed++;
				case FAILED -> failed++;
				case UNKNOWN -> unknown++;
				case SKIPPED -> skipped++;
				case NOT_CLAIMED -> { }
			}
		}
		return new RenewalResult(renewed, failed, unknown, expired, skipped);
	}

	/** 해지 예약 구독의 기간이 끝났다. 결제하지 않고 무료로 바꾼다 */
	private boolean expire(Long id, LocalDateTime now) {
		if (subscriptions.changeStatusIfDue(id, SubscriptionStatus.CANCEL_REQUESTED, SubscriptionStatus.EXPIRED, now) != 1) return false;
		Subscription subscription = subscriptions.findWithMemberAndPlanById(id).orElseThrow();
		subscription.expire(now);
		subscription.getMember().downgradeFromPremium();
		return true;
	}

	private Outcome renew(Long id, LocalDateTime now) {
		Claim claim;
		try {
			claim = transaction.execute(status -> claim(id, now));
		} catch (IllegalStateException e) {
			// 저장된 결제 수단을 읽지 못했다(암호화 키 변경 등). 구독을 잡지 않고 다음 실행으로 미룬다.
			log.error("매월 결제 수단을 읽지 못해 결제를 미룹니다: subscriptionId={}", id);
			return Outcome.SKIPPED;
		}
		if (claim == null) return Outcome.NOT_CLAIMED;

		StoredPaymentMethod method = claim.method();
		PaidPayment paid;
		try {
			paid = portOne.payWithBillingKey(claim.orderId(), method.billingKey(), claim.orderName(), method.customerId(), claim.amount(), claim.currency());
		} catch (PortOneException e) {
			if (e.getKind() == PortOneException.Kind.UNKNOWN) {
				log.error("매월 결제 결과를 알 수 없어 RENEWING으로 남깁니다: subscriptionId={}, orderId={}. 포트원 결제 조회로 확인이 필요합니다.", id, claim.orderId());
				return Outcome.UNKNOWN;
			}
			transaction.executeWithoutResult(status -> fail(claim, now));
			deleteBillingKey(method.billingKey());
			log.info("매월 결제 실패로 구독을 취소했습니다: subscriptionId={}, type={}", id, e.getType());
			return Outcome.FAILED;
		}
		transaction.executeWithoutResult(status -> succeed(claim, paid, now));
		return Outcome.RENEWED;
	}

	/** ACTIVE인 구독을 RENEWING으로 바꾸고 PENDING 결제 행을 만든다. 다른 실행이 먼저 잡았으면 null */
	private Claim claim(Long id, LocalDateTime now) {
		if (subscriptions.changeStatusIfDue(id, SubscriptionStatus.ACTIVE, SubscriptionStatus.RENEWING, now) != 1) return null;
		Subscription subscription = subscriptions.findWithMemberAndPlanById(id).orElseThrow();
		StoredPaymentMethod method = paymentMethods.read(subscription.getMember())
				.orElseThrow(() -> new IllegalStateException("등록된 결제 수단이 없습니다."));
		SubscriptionPlan plan = subscription.getPlan();
		String orderId = UUID.randomUUID().toString();
		PaymentProvider provider = method.provider();
		Payment payment = payments.save(Payment.pending(subscription, orderId, provider, Map.of("provider", provider.name(), "display", method.display()),
				plan.getPrice(), plan.getCurrency(), now));
		return new Claim(id, payment.getId(), orderId, method, "DevMozip " + plan.getName() + " 월 구독", plan.amountInWon(), plan.getCurrency());
	}

	private void succeed(Claim claim, PaidPayment paid, LocalDateTime now) {
		Payment payment = payments.findById(claim.paymentId()).orElseThrow();
		payment.markPaid(paid.pgTxId(), toKst(paid.paidAt(), now));
		Subscription subscription = subscriptions.findWithMemberAndPlanById(claim.subscriptionId()).orElseThrow();
		long paidCount = payments.countBySubscriptionIdAndStatus(subscription.getId(), PaymentStatus.PAID);
		subscription.renew(subscription.periodEndAfter(paidCount));
	}

	/** 결제 실패: 구독을 바로 취소하고 결제 수단을 비운 뒤 무료로 바꾼다 */
	private void fail(Claim claim, LocalDateTime now) {
		payments.findById(claim.paymentId()).orElseThrow().markFailed();
		Subscription subscription = subscriptions.findWithMemberAndPlanById(claim.subscriptionId()).orElseThrow();
		subscription.cancelByPaymentFailure(now);
		Member member = subscription.getMember();
		paymentMethods.clear(member);
		member.downgradeFromPremium();
	}

	private void deleteBillingKey(String billingKey) {
		try {
			portOne.deleteBillingKey(billingKey);
		} catch (PortOneException e) {
			log.warn("결제 실패 후 빌링키 삭제 실패: kind={}, type={}. 포트원 콘솔에서 확인이 필요합니다.", e.getKind(), e.getType());
		}
	}

	private static LocalDateTime toKst(String value, LocalDateTime fallback) {
		try {
			return OffsetDateTime.parse(value).atZoneSameInstant(KST).toLocalDateTime();
		} catch (DateTimeParseException | NullPointerException e) {
			return fallback;
		}
	}
}
