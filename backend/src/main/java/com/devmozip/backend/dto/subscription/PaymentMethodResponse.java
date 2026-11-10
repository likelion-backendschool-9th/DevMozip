package com.devmozip.backend.dto.subscription;

/** 17.5 결제 수단. 표시 정보만 담고 빌링키는 넣지 않는다 */
public record PaymentMethodResponse(String provider, String display) {
}
