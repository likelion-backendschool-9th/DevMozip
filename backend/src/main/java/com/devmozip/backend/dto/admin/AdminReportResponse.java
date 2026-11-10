package com.devmozip.backend.dto.admin;

import java.time.LocalDateTime;

/** 관리자 신고 내역 항목 (WBS 14.8). type은 PROJECT·MEMBER·USER·POST·COMMENT다. */
public record AdminReportResponse(long id, String type, AdminPartyResponse reporter, AdminReportTargetResponse target, String reason,
	String status, String resolution, String handledByNickname, LocalDateTime handledAt, LocalDateTime createdAt) {
}
