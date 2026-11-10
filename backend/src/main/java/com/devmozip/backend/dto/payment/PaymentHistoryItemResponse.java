package com.devmozip.backend.dto.payment;

import java.time.OffsetDateTime;

import com.devmozip.backend.dto.subscription.SubscriptionPlanResponse;
import com.devmozip.backend.entity.Payment;
import com.devmozip.backend.entity.enums.PaymentStatus;

/** 13.5 결제 내역 항목. occurredAt은 PAID면 결제 완료 시각, 그 밖에는 결제를 시도한 시각이다 */
public record PaymentHistoryItemResponse(Long id, OffsetDateTime occurredAt, String planName, String provider, String paymentMethod,
		long amount, String currency, String status) {

	public static PaymentHistoryItemResponse from(Payment payment) {
		String display = payment.getPaymentMethodSnapshot() == null ? null : payment.getPaymentMethodSnapshot().get("display");
		return new PaymentHistoryItemResponse(payment.getId(),
				SubscriptionPlanResponse.kst(payment.getStatus() == PaymentStatus.PAID && payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt()),
				payment.getSubscription().getPlan().getName(), payment.getProvider().name(),
				display == null || display.isBlank() ? payment.getProvider().getLabel() : display,
				payment.getAmount().longValueExact(), payment.getCurrency(), payment.getStatus().name());
	}
}
