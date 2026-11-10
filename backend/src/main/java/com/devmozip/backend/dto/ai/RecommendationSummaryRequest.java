package com.devmozip.backend.dto.ai;

import com.devmozip.backend.entity.AiRecommendationRequest;
import java.time.LocalDateTime;

public record RecommendationRequestSummary(long requestId, AiRecommendationRequest.Status status, LocalDateTime createdAt) {}
