package com.devmozip.backend.dto.feed;

import java.time.OffsetDateTime;

/** 접수한 신고. targetType은 POST 또는 COMMENT, status는 접수 직후 AWATING이다. */
public record FeedReportResponse(long id, String targetType, long targetId, String status, OffsetDateTime createdAt) {}
