package com.devmozip.backend.service;

import com.devmozip.backend.dto.ai.RecommendationResponse;

public interface AiRecommendationExecutionService {
	record Execution(RecommendationResponse response, boolean created) {}
	Execution execute(long conversationId, Long messageId, String key);
	RecommendationResponse find(long conversationId, Long requestId);
}
