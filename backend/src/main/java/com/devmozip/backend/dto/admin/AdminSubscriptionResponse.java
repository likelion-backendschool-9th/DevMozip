package com.devmozip.backend.dto.admin;

import java.time.LocalDateTime;
import com.devmozip.backend.entity.Subscription;

/**
 * 관리자 구독 목록 항목 (WBS 14.12). 탈퇴로 끝난 구독(#135)도 CANCELED로 남으며 member.withdrawn으로 구분한다.
 * 결제 수단과 빌링키는 넣지 않는다.
 */
public record AdminSubscriptionResponse(long id, AdminPartyResponse member, String planName, String status, LocalDateTime startsAt,
	LocalDateTime currentPeriodEnd, LocalDateTime cancelRequestedAt, LocalDateTime endedAt) {

	public static AdminSubscriptionResponse of(Subscription subscription) {
		return new AdminSubscriptionResponse(subscription.getId(), AdminPartyResponse.of(subscription.getMember()), subscription.getPlan().getName(),
			subscription.getStatus().name(), subscription.getStartsAt(), subscription.getCurrentPeriodEnd(), subscription.getCancelRequestedAt(),
			subscription.getEndedAt());
	}
}
