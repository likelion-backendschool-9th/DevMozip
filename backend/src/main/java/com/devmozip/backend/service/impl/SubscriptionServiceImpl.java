package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.devmozip.backend.client.PortOneClient;
import com.devmozip.backend.client.PortOneException;
import com.devmozip.backend.dto.subscription.PaymentMethodResponse;
import com.devmozip.backend.dto.subscription.SubscriptionPlanResponse;
import com.devmozip.backend.dto.subscription.SubscriptionResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberRole;
import com.devmozip.backend.entity.Subscription;
import com.devmozip.backend.entity.enums.SubscriptionStatus;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.SubscriptionPlanRepository;
import com.devmozip.backend.repository.SubscriptionRepository;
import com.devmozip.backend.security.payment.PaymentMethodCodec;
import com.devmozip.backend.security.payment.StoredPaymentMethod;
import com.devmozip.backend.service.SubscriptionService;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {
	private static final Logger log = LoggerFactory.getLogger(SubscriptionServiceImpl.class);

	private final SubscriptionPlanRepository plans;
	private final SubscriptionRepository subscriptions;
	private final MemberRepository members;
	private final PaymentMethodCodec paymentMethods;
	private final PortOneClient portOne;
	private final TransactionTemplate transaction;
	private final Clock clock;

	public SubscriptionServiceImpl(SubscriptionPlanRepository plans, SubscriptionRepository subscriptions, MemberRepository members,
			PaymentMethodCodec paymentMethods, PortOneClient portOne, PlatformTransactionManager transactionManager,
			@Qualifier("paymentClock") Clock clock) {
		this.plans = plans;
		this.subscriptions = subscriptions;
		this.members = members;
		this.paymentMethods = paymentMethods;
		this.portOne = portOne;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<SubscriptionPlanResponse> plans() {
		return plans.findAllByActiveTrueOrderByIdAsc().stream().map(SubscriptionPlanResponse::from).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public SubscriptionResponse me(String email) {
		Member member = PaymentMembers.load(members, email);
		return toResponse(member, subscriptions.findFirstByMemberIdOrderByIdDesc(member.getId()));
	}

	@Override
	public SubscriptionResponse cancel(String email) {
		// 1) 회원을 잠그고 해지 예약으로 바꾼 뒤 결제 수단을 비운다. 스케줄러는 이 상태를 결제하지 않는다.
		Cancelled cancelled = transaction.execute(status -> {
			Member member = PaymentMembers.lock(members, email);
			Subscription current = subscriptions.findFirstByMemberIdAndStatusIn(member.getId(), SubscriptionStatus.CURRENT)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "해지할 수 있는 구독이 없습니다."));
			if (current.getStatus() == SubscriptionStatus.CANCEL_REQUESTED)
				throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 해지 예약된 구독입니다.");
			if (current.getStatus() == SubscriptionStatus.RENEWING)
				throw new ResponseStatusException(HttpStatus.CONFLICT, "이번 달 결제를 처리하고 있습니다. 잠시 후 다시 시도해 주세요.");
			String billingKey = readBillingKey(member);
			current.requestCancel(LocalDateTime.now(clock));
			paymentMethods.clear(member);
			return new Cancelled(SubscriptionResponse.of(current, member.getRole() == MemberRole.PREMIUM, null), billingKey);
		});
		// 2) 커밋한 뒤 포트원 빌링키를 삭제한다. 실패해도 해지 예약 상태라 결제는 일어나지 않는다.
		deleteBillingKey(cancelled.billingKey(), "해지");
		return cancelled.response();
	}

	private record Cancelled(SubscriptionResponse response, String billingKey) {
	}

	private SubscriptionResponse toResponse(Member member, Optional<Subscription> latest) {
		boolean premium = member.getRole() == MemberRole.PREMIUM;
		if (latest.isEmpty()) return SubscriptionResponse.none(premium);
		PaymentMethodResponse method = paymentMethods.readDisplay(member)
				.map(value -> new PaymentMethodResponse(value.get("provider"), value.get("display"))).orElse(null);
		return SubscriptionResponse.of(latest.get(), premium, method);
	}

	private String readBillingKey(Member member) {
		try {
			return paymentMethods.read(member).map(StoredPaymentMethod::billingKey).orElse(null);
		} catch (IllegalStateException e) {
			log.warn("저장된 빌링키를 읽지 못해 포트원에서 삭제하지 못합니다: memberId={}", member.getId());
			return null;
		}
	}

	private void deleteBillingKey(String billingKey, String reason) {
		if (billingKey == null) return;
		try {
			portOne.deleteBillingKey(billingKey);
		} catch (PortOneException e) {
			log.warn("{} 후 빌링키 삭제 실패: kind={}, type={}. 포트원 콘솔에서 확인이 필요합니다.", reason, e.getKind(), e.getType());
		}
	}
}
