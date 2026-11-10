package com.devmozip.backend.entity.enums;

import java.util.EnumSet;
import java.util.Set;

/** 구독 상태 (docs/premium-payment-plan.md 4절). 저장 값은 이름 그대로다. */
public enum SubscriptionStatus {
	/** 이용 중. 기간이 끝나면 매월 결제한다 */
	ACTIVE,
	/** 매월 결제 처리 중. 스케줄러가 이 상태로 바꿔 다른 실행이 같은 구독을 잡지 못하게 한다 */
	RENEWING,
	/** 해지 예약. 기간 끝까지 이용하고 결제하지 않는다 */
	CANCEL_REQUESTED,
	/** 해지 예약 후 기간 종료 */
	EXPIRED,
	/** 매월 결제 실패로 취소 */
	CANCELED;

	/** 이용 기간 안에 있는 상태. 회원당 하나만 있을 수 있다 */
	public static final Set<SubscriptionStatus> CURRENT = EnumSet.of(ACTIVE, RENEWING, CANCEL_REQUESTED);

	public boolean isCurrent() {
		return CURRENT.contains(this);
	}
}
