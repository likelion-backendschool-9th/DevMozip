package com.devmozip.backend.dto.admin;

import java.time.LocalDateTime;

/** 대시보드의 최근 운영 변경 하나 (WBS 14.1, D7). type은 SANCTION(제재)·REPORT(신고 처리)다. */
public record AdminRecentActionResponse(String type, String actorNickname, String summary, LocalDateTime createdAt) {
}
