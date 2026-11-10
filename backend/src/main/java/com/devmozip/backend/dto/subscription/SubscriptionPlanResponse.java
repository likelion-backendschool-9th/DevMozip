package com.devmozip.backend.dto.subscription;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

import com.devmozip.backend.entity.SubscriptionPlan;

/** 13.1 상품 안내. 금액은 원 단위 정수다 */
public record SubscriptionPlanResponse(Long id, String name, long price, String currency, String billingCycle, Map<String, Integer> featureLimits) {
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public static SubscriptionPlanResponse from(SubscriptionPlan plan) {
		return new SubscriptionPlanResponse(plan.getId(), plan.getName(), plan.amountInWon(), plan.getCurrency(), plan.getBillingCycle(),
				plan.getFeatureLimits() == null ? Map.of() : plan.getFeatureLimits());
	}

	/** DB의 한국 시각을 시간대가 붙은 값으로 내보낸다. 다른 시간대의 브라우저에서도 같은 순간으로 읽힌다 */
	public static OffsetDateTime kst(LocalDateTime value) {
		return value == null ? null : value.atZone(KST).toOffsetDateTime();
	}
}
