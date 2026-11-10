package com.devmozip.backend.entity.enums;

/** 결제 상태. 첫 결제는 승인과 저장이 한 요청에서 끝나 PAID만 남고, PENDING은 매월 결제에만 쓴다. */
public enum PaymentStatus {
	/** 매월 결제 처리 중 (포트원 응답 전) */
	PENDING,
	PAID,
	FAILED,
	/** 결제 후 저장에 실패해 결제를 취소한 경우 */
	CANCELED
}
