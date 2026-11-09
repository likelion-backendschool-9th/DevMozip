package com.devmozip.backend.service;

import com.devmozip.backend.dto.ai.internal.RecommendationResult;
import com.devmozip.backend.dto.ai.UpdateConditionsRequest;

public interface AiRecommendationService {
	RecommendationResult recommend(UpdateConditionsRequest conditions, String message);
}
