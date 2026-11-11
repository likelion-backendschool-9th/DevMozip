package com.devmozip.backend.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.devmozip.backend.entity.Payment;

/**
 * 관리자 결제 내역 항목 (WBS 14.13). method는 결제 당시 표시용 결제 수단(스냅숏의 display)이다.
 * 빌링키와 결제 수단 원문은 넣지 않는다. 탈퇴 회원의 결제 기록도 남아 member.withdrawn으로 구분한다.
 */
public record AdminPaymentResponse(long id, String orderId, AdminPartyResponse member, BigDecimal amount, String currency, String status,
	String provider, String method, LocalDateTime paidAt, LocalDateTime createdAt) {

	public static AdminPaymentResponse of(Payment payment) {
		var snapshot = payment.getPaymentMethodSnapshot();
		String method = snapshot == null ? null : snapshot.get("display");
		return new AdminPaymentResponse(payment.getId(), payment.getOrderId(), AdminPartyResponse.of(payment.getSubscription().getMember()),
			payment.getAmount(), payment.getCurrency(), payment.getStatus().name(), payment.getProvider().name(), method, payment.getPaidAt(),
			payment.getCreatedAt());
	}
}
