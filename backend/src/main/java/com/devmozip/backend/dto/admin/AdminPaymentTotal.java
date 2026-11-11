package com.devmozip.backend.dto.admin;

import java.math.BigDecimal;

/** 결제 완료(PAID) 금액의 통화별 합 (WBS 14.13). */
public record AdminPaymentTotal(String currency, BigDecimal amount) {
}
