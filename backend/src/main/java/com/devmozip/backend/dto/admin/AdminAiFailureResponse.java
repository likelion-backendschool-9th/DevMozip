package com.devmozip.backend.dto.admin;

import java.time.LocalDateTime;

/**
 * WBS 14.11 실패(FAILED)한 AI 추천 요청. 오류 원인 칼럼이 없어 시각·회원·상태만 준다(docs/admin-plan.md D6).
 * AI 추천 조건과 대화 내용은 넣지 않는다.
 */
public record AdminAiFailureResponse(long requestId, long conversationId, Long userId, String userTag, String nickname, boolean withdrawn,
	String status, LocalDateTime createdAt, LocalDateTime completedAt) {
}
