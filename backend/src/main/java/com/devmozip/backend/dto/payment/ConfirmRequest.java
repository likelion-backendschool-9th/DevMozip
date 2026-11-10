package com.devmozip.backend.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 13.2 결제 승인. 빌링키는 브라우저를 거쳐 오므로 서버가 포트원에 조회해 확인한 뒤 결제한다 */
public record ConfirmRequest(
		@NotBlank(message = "주문 번호가 필요합니다.") @Size(max = 64, message = "주문 번호를 확인해 주세요.") String orderId,
		@NotBlank(message = "결제 수단 등록 정보가 필요합니다.") @Size(max = 200, message = "결제 수단 등록 정보를 확인해 주세요.") String billingKey) {

	@Override
	public String toString() {
		// 빌링키가 로그에 찍히지 않도록 기본 toString을 쓰지 않는다.
		return "ConfirmRequest[orderId=" + orderId + "]";
	}
}
