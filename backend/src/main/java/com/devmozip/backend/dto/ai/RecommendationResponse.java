package com.devmozip.backend.dto.ai;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.devmozip.backend.entity.AiRecommendation;
import com.devmozip.backend.entity.AiRecommendationRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record RecommendationResponse(long requestId, long conversationId, Long messageId,
	AiRecommendationRequest.Status status, @JsonRawValue String conditions,
	LocalDateTime createdAt, LocalDateTime completedAt, List<Item> developers, List<Item> projects) {
	public record Item(long id, Long recommendedUserId, Long recommendedProjectId, BigDecimal matchScore,
		String basicReason, int rank, LocalDateTime createdAt) {
		public static Item from(AiRecommendation result) {
			return new Item(result.getId(), result.getUserId(), result.getProjectId(), result.getMatchScore(),
				result.getBasicReason(), result.getRank(), result.getCreatedAt());
		}
	}
}
