package com.devmozip.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.devmozip.backend.client.PortOneClient;
import com.devmozip.backend.client.PortOneClient.BillingKeyInfo;
import com.devmozip.backend.client.PortOneClient.PaidPayment;
import com.devmozip.backend.client.PortOneException;
import com.devmozip.backend.config.PaymentProperties;
import com.devmozip.backend.dto.payment.CheckoutRequest;
import com.devmozip.backend.dto.payment.CheckoutResponse;
import com.devmozip.backend.dto.payment.ConfirmRequest;
import com.devmozip.backend.dto.payment.PaymentHistoryItemResponse;
import com.devmozip.backend.dto.payment.PaymentHistoryPageResponse;
import com.devmozip.backend.dto.subscription.PaymentMethodResponse;
import com.devmozip.backend.dto.subscription.SubscriptionResponse;
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
import com.devmozip.backend.security.payment.PaymentMethodCodec;
import com.devmozip.backend.security.payment.StoredPaymentMethod;
import com.devmozip.backend.service.PaymentCheckoutService;
import com.devmozip.backend.service.PaymentCheckoutService.PendingCheckout;
import com.devmozip.backend.service.PaymentService;

/**
 * 첫 결제 흐름 (docs/premium-payment-plan.md 3절).
 * 포트원을 부르는 동안에는 DB 잠금을 잡지 않는다. 결제는 됐는데 저장하지 못하면 결제를 취소하고 빌링키를 삭제한다.
 */
@Service
public class PaymentServiceImpl implements PaymentService {
	private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final int MAX_PAGE_SIZE = 100;

	static final String UNAVAILABLE = "지금은 결제를 진행할 수 없습니다. 잠시 후 다시 시도해 주세요.";
	static final String CHECKOUT_EXPIRED = "결제 정보를 찾을 수 없거나 결제 시간이 지났습니다. 처음부터 다시 결제해 주세요.";
	static final String BILLING_KEY_INVALID = "결제 수단 등록을 확인하지 못했습니다. 처음부터 다시 결제해 주세요.";
	static final String REJECTED = "결제가 승인되지 않았습니다. 결제 수단을 확인한 뒤 다시 시도해 주세요.";
	static final String UNKNOWN_RESULT = "결제 결과를 확인하지 못했습니다. 결제 내역을 확인한 뒤 다시 시도해 주세요.";
	static final String ALREADY_SUBSCRIBED = "이미 프리미엄을 이용 중이거나 해지 예약 중입니다.";
	static final String NOT_ALLOWED = "이 계정은 구독 결제 대상이 아닙니다.";

	private final PaymentProperties properties;
	private final PaymentCheckoutService checkouts;
	private final PortOneClient portOne;
	private final SubscriptionPlanRepository plans;
	private final SubscriptionRepository subscriptions;
	private final PaymentRepository payments;
	private final MemberRepository members;
	private final PaymentMethodCodec paymentMethods;
	private final TransactionTemplate transaction;
	private final Clock clock;

	public PaymentServiceImpl(PaymentProperties properties, PaymentCheckoutService checkouts, PortOneClient portOne,
			SubscriptionPlanRepository plans, SubscriptionRepository subscriptions, PaymentRepository payments, MemberRepository members,
			PaymentMethodCodec paymentMethods, PlatformTransactionManager transactionManager, @Qualifier("paymentClock") Clock clock) {
		this.properties = properties;
		this.checkouts = checkouts;
		this.portOne = portOne;
		this.plans = plans;
		this.subscriptions = subscriptions;
		this.payments = payments;
		this.members = members;
		this.paymentMethods = paymentMethods;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public CheckoutResponse checkout(String email, CheckoutRequest request) {
		PaymentProvider provider = request.provider();
		if (!properties.isConfigured(provider)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
		Member member = PaymentMembers.load(members, email);
		requireEligible(member);
		SubscriptionPlan plan = plans.findByIdAndActiveTrue(request.planId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "판매하지 않는 상품입니다."));
		String orderId = UUID.randomUUID().toString();
		String customerId = UUID.randomUUID().toString();
		checkouts.save(new PendingCheckout(orderId, member.getId(), plan.getId(), provider, plan.amountInWon(), plan.getCurrency(), customerId));
		return new CheckoutResponse(orderId, provider.name(), properties.getStoreId(), properties.channelKey(provider), provider.getBillingKeyMethod(),
				orderName(plan), customerId, properties.callbackUrl(orderId));
	}

	@Override
	public SubscriptionResponse confirm(String email, ConfirmRequest request) {
		String orderId = request.orderId();
		String billingKey = request.billingKey();
		// 꺼내면서 지우므로 같은 주문의 두 번째 승인 요청은 여기서 끝난다.
		PendingCheckout pending = checkouts.consume(orderId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, CHECKOUT_EXPIRED));
		Member member = PaymentMembers.load(members, email);
		if (pending.memberId() != member.getId()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, CHECKOUT_EXPIRED);
		if (!properties.isConfigured(pending.provider())) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);

		String display = verifyBillingKey(pending, billingKey);
		try {
			requireEligible(member);
		} catch (ResponseStatusException e) {
			deleteBillingKey(billingKey, "결제 대상 아님");
			throw e;
		}
		SubscriptionPlan plan = plans.findById(pending.planId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "판매하지 않는 상품입니다."));

		PaidPayment paid = pay(pending, billingKey, orderName(plan));
		try {
			return transaction.execute(status -> save(email, pending, plan, billingKey, display, paid));
		} catch (RuntimeException e) {
			// 결제는 됐지만 구독을 만들지 못했다. 청구가 남지 않도록 되돌린다.
			cancelPayment(orderId, "구독 저장 실패");
			deleteBillingKey(billingKey, "구독 저장 실패");
			if (e instanceof ResponseStatusException status && status.getStatusCode() == HttpStatus.CONFLICT) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 이용 중인 구독이 있어 이번 결제를 취소했습니다.");
			}
			log.error("결제 후 구독 저장 실패로 결제를 취소했습니다: orderId={}, error={}", orderId, e.getClass().getSimpleName());
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "결제를 처리하지 못해 결제를 취소했습니다. 다시 시도해 주세요.");
		}
	}

	@Override
	@Transactional(readOnly = true)
	public PaymentHistoryPageResponse history(String email, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 범위를 확인해 주세요.");
		Member member = PaymentMembers.load(members, email);
		Slice<Payment> slice = payments.findHistory(member.getId(), PageRequest.of(page, size));
		return new PaymentHistoryPageResponse(slice.getContent().stream().map(PaymentHistoryItemResponse::from).toList(), page, size, slice.hasNext());
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<PaymentMethodResponse> paymentMethod(String email) {
		Member member = PaymentMembers.load(members, email);
		return paymentMethods.readDisplay(member).map(value -> new PaymentMethodResponse(value.get("provider"), value.get("display")));
	}

	/** 결제를 시작할 수 있는 회원: USER이고 이용 기간 안의 구독이 없다 */
	private void requireEligible(Member member) {
		if (member.getRole() == MemberRole.PREMIUM) throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_SUBSCRIBED);
		if (member.getRole() != null && member.getRole() != MemberRole.USER) throw new ResponseStatusException(HttpStatus.FORBIDDEN, NOT_ALLOWED);
		if (subscriptions.existsByMemberIdAndStatusIn(member.getId(), SubscriptionStatus.CURRENT))
			throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_SUBSCRIBED);
	}

	/**
	 * 빌링키가 이 주문으로, 고른 결제 수단의 채널에서 발급됐는지 포트원에 확인한다. 화면에 보여 줄 결제 수단 정보를 돌려준다.
	 * 다른 주문(다른 회원)의 빌링키는 우리 것이 아니므로 삭제하지 않는다.
	 */
	private String verifyBillingKey(PendingCheckout pending, String billingKey) {
		BillingKeyInfo info;
		try {
			info = portOne.getBillingKey(billingKey);
		} catch (PortOneException e) {
			if (e.getKind() == PortOneException.Kind.UNKNOWN) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, BILLING_KEY_INVALID);
		}
		if (!pending.orderId().equals(info.issueId())) {
			log.warn("주문과 다른 발급 건의 빌링키로 승인을 요청했습니다: orderId={}", pending.orderId());
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, BILLING_KEY_INVALID);
		}
		if (!info.issued() || !info.channelKeys().contains(properties.channelKey(pending.provider()))) {
			deleteBillingKey(billingKey, "빌링키 확인 실패");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, BILLING_KEY_INVALID);
		}
		return StringUtils.hasText(info.display()) ? info.display() : pending.provider().getLabel();
	}

	private PaidPayment pay(PendingCheckout pending, String billingKey, String orderName) {
		try {
			return portOne.payWithBillingKey(pending.orderId(), billingKey, orderName, pending.customerId(), pending.amount(), pending.currency());
		} catch (PortOneException e) {
			if (e.getKind() == PortOneException.Kind.UNKNOWN) {
				// 결제가 됐을 수도 있다. 청구가 남지 않도록 취소를 시도한다(결제되지 않았다면 취소는 실패한다).
				cancelPayment(pending.orderId(), "결제 결과 확인 실패");
				deleteBillingKey(billingKey, "결제 결과 확인 실패");
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, UNKNOWN_RESULT);
			}
			deleteBillingKey(billingKey, "첫 결제 거절");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, REJECTED);
		}
	}

	private SubscriptionResponse save(String email, PendingCheckout pending, SubscriptionPlan plan, String billingKey, String display, PaidPayment paid) {
		// 포트원을 부르는 사이 다른 결제가 먼저 끝났을 수 있으므로 잠근 뒤 다시 확인한다.
		Member member = PaymentMembers.lock(members, email);
		requireEligible(member);
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDateTime paidAt = toKst(paid.paidAt(), now);
		Subscription subscription = subscriptions.save(Subscription.start(member, plan, paidAt));
		payments.save(Payment.paid(subscription, pending.orderId(), pending.provider(), Map.of("provider", pending.provider().name(), "display", display),
				BigDecimal.valueOf(pending.amount()), pending.currency(), paid.pgTxId(), paidAt, now));
		paymentMethods.store(member, new StoredPaymentMethod(pending.provider(), pending.customerId(), billingKey, display, now));
		member.upgradeToPremium();
		return SubscriptionResponse.of(subscription, true, new PaymentMethodResponse(pending.provider().name(), display));
	}

	private static String orderName(SubscriptionPlan plan) {
		return "DevMozip " + plan.getName() + " 월 구독";
	}

	private static LocalDateTime toKst(String value, LocalDateTime fallback) {
		try {
			return OffsetDateTime.parse(value).atZoneSameInstant(KST).toLocalDateTime();
		} catch (DateTimeParseException e) {
			return fallback;
		}
	}

	private void cancelPayment(String orderId, String reason) {
		try {
			portOne.cancelPayment(orderId, reason);
		} catch (PortOneException e) {
			log.warn("결제 취소 실패: orderId={}, kind={}, type={}. 포트원 콘솔에서 확인이 필요합니다.", orderId, e.getKind(), e.getType());
		}
	}

	private void deleteBillingKey(String billingKey, String reason) {
		try {
			portOne.deleteBillingKey(billingKey);
		} catch (PortOneException e) {
			log.warn("{} 후 빌링키 삭제 실패: kind={}, type={}", reason, e.getKind(), e.getType());
		}
	}
}
