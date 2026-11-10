package com.devmozip.backend.dto.ai;

import com.fasterxml.jackson.annotation.JsonRawValue;

/** 재전송 응답의 currentConditions/analysis는 조회 시점의 최신 대화 상태다. */
public record MessageAnalysisResponse(MessageResponse userMessage, MessageResponse assistantMessage,
	@JsonRawValue String currentConditions, @JsonRawValue String analysis, boolean readyForRecommendation, boolean replayed) {}
