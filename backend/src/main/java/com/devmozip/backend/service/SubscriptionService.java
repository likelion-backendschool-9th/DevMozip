package com.devmozip.backend.service;

import java.util.List;

import com.devmozip.backend.dto.subscription.SubscriptionPlanResponse;
import com.devmozip.backend.dto.subscription.SubscriptionResponse;

/** 프리미엄 상품 안내(13.1), 구독 상태(13.3), 해지(13.4). 요청자는 인증 정보의 이메일로만 찾는다 */
public interface SubscriptionService {

	List<SubscriptionPlanResponse> plans();

	SubscriptionResponse me(String email);

	/** 다음 결제를 멈추고 빌링키를 삭제한다. 이미 결제한 기간 끝까지 프리미엄을 유지한다 */
	SubscriptionResponse cancel(String email);
}
