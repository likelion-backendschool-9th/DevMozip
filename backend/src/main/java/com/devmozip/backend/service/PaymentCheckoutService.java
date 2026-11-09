package com.devmozip.backend.service;

import java.util.Optional;

import com.devmozip.backend.entity.enums.PaymentProvider;

/**
 * 첫 결제의 결제 대기 정보(주문)를 승인 전까지 보관한다. payments 행은 구독에 속하므로 구독이 생기기 전에는 쓸 수 없다.
 * 수명은 PaymentProperties.checkoutTtl(기본 15분)이며, 승인 때 꺼내면서 지워 같은 주문을 두 번 승인하지 못하게 한다.
 *
 * - local 프로필: InMemoryPaymentCheckoutService. 서버를 재시작하면 사라진다.
 * - 그 외(서버·compose·테스트): RedisPaymentCheckoutService. 블루그린의 두 백엔드가 같은 값을 본다.
 */
public interface PaymentCheckoutService {

	void save(PendingCheckout checkout);

	/** 주문을 꺼내고 바로 지운다. 동시에 요청해도 한 번만 돌려주고, 없거나 만료됐으면 비어 있다 */
	Optional<PendingCheckout> consume(String orderId);

	/** 결제 준비 때 서버가 정한 값. 금액은 원 단위 정수다 */
	record PendingCheckout(String orderId, long memberId, long planId, PaymentProvider provider, long amount, String currency, String customerId) {
	}
}
