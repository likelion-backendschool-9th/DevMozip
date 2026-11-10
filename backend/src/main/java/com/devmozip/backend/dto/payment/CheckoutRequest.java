package com.devmozip.backend.dto.payment;

import com.devmozip.backend.entity.enums.PaymentProvider;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 13.2 결제 준비. 금액은 받지 않고 서버의 상품 가격으로 정한다 */
public record CheckoutRequest(
		@NotNull(message = "상품을 선택해 주세요.") @Positive(message = "상품을 확인해 주세요.") Long planId,
		@NotNull(message = "결제 수단을 선택해 주세요.") PaymentProvider provider) {
}
