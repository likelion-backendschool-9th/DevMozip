package com.devmozip.backend.dto.subscription;

import static com.devmozip.backend.dto.subscription.SubscriptionPlanResponse.kst;

import java.time.OffsetDateTime;

import com.devmozip.backend.entity.Subscription;
import com.devmozip.backend.entity.enums.SubscriptionStatus;

/**
 * 13.3 구독 상태. status는 구독한 적이 없으면 NONE, 그 밖에는 가장 최근 구독의 상태다.
 * premium은 현재 역할이 PREMIUM인지이며, nextBillingAt은 ACTIVE일 때만 있다.
 */
public record SubscriptionResponse(String status, boolean premium, SubscriptionPlanResponse plan, OffsetDateTime startsAt,
		OffsetDateTime currentPeriodEnd, OffsetDateTime nextBillingAt, OffsetDateTime cancelRequestedAt, OffsetDateTime endedAt,
		PaymentMethodResponse paymentMethod) {

	public static SubscriptionResponse none(boolean premium) {
		return new SubscriptionResponse("NONE", premium, null, null, null, null, null, null, null);
	}

	public static SubscriptionResponse of(Subscription subscription, boolean premium, PaymentMethodResponse paymentMethod) {
		boolean active = subscription.getStatus() == SubscriptionStatus.ACTIVE;
		return new SubscriptionResponse(subscription.getStatus().name(), premium, SubscriptionPlanResponse.from(subscription.getPlan()),
				kst(subscription.getStartsAt()), kst(subscription.getCurrentPeriodEnd()), active ? kst(subscription.getCurrentPeriodEnd()) : null,
				kst(subscription.getCancelRequestedAt()), kst(subscription.getEndedAt()),
				subscription.getStatus().isCurrent() ? paymentMethod : null);
	}
}
