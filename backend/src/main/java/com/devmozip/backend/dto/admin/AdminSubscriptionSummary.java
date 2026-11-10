package com.devmozip.backend.dto.admin;

import java.util.Map;

/**
 * 구독 요약 (WBS 14.12). statusCounts는 상태별 구독 수(없는 상태는 0)이고,
 * current는 이용 기간 안의 구독(ACTIVE·RENEWING·CANCEL_REQUESTED) 수다.
 */
public record AdminSubscriptionSummary(Map<String, Long> statusCounts, long current) {
}
