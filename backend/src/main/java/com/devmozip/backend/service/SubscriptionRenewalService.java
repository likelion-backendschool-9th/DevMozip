package com.devmozip.backend.service;

import java.time.LocalDateTime;

/**
 * 매월 결제와 해지 예약 만료 (docs/premium-payment-plan.md 3절 "매월 결제").
 * 시각을 인자로 받아 테스트에서 바꿀 수 있게 한다. 스케줄러는 이 서비스만 부른다.
 */
public interface SubscriptionRenewalService {

	/** now 기준으로 기간이 끝난 구독을 처리하고 결과 수를 돌려준다 */
	RenewalResult run(LocalDateTime now);

	/**
	 * renewed: 매월 결제 성공, failed: 결제 실패로 구독 취소, unknown: 결과를 몰라 RENEWING으로 남김,
	 * expired: 해지 예약 만료, skipped: 결제 수단을 읽지 못해 다음 실행으로 미룸
	 */
	record RenewalResult(int renewed, int failed, int unknown, int expired, int skipped) {
		public boolean isEmpty() {
			return renewed + failed + unknown + expired + skipped == 0;
		}
	}
}
