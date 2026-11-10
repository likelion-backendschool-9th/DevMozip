package com.devmozip.backend.dto.admin;

import java.util.List;

/** 결제 요약 (WBS 14.13). paidTotals는 전체 결제 완료 금액의 통화별 합이다. */
public record AdminPaymentSummary(List<AdminPaymentTotal> paidTotals) {
}
