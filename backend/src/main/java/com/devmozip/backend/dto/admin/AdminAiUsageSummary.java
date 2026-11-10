package com.devmozip.backend.dto.admin;

/** WBS 14.10 AI 사용량 요약. month는 한국 시각 YYYY-MM이고, members는 그 달 사용 기록이 있는 회원 수다. */
public record AdminAiUsageSummary(String month, long totalUsed, long totalPending, long members) {
}
