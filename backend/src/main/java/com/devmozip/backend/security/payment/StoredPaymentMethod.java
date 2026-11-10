package com.devmozip.backend.security.payment;

import java.time.LocalDateTime;

import com.devmozip.backend.entity.enums.PaymentProvider;

/**
 * users.payment_methods에 두는 현재 결제 수단. billingKey는 복호화한 원문이며 응답·로그에 넣지 않는다.
 * customerId는 빌링키를 발급할 때 쓴 구매자 ID로, 매월 결제 요청에 다시 넣는다.
 */
public record StoredPaymentMethod(PaymentProvider provider, String customerId, String billingKey, String display, LocalDateTime registeredAt) {
	@Override
	public String toString() {
		// 빌링키가 로그에 찍히지 않도록 기본 toString을 쓰지 않는다.
		return "StoredPaymentMethod[provider=" + provider + ", display=" + display + "]";
	}
}
