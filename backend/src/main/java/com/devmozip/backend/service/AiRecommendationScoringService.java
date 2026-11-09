package com.devmozip.backend.service;

import com.devmozip.backend.dto.ai.internal.DeveloperCandidate;
import com.devmozip.backend.dto.ai.internal.RecommendationResult.Score;
import com.devmozip.backend.dto.ai.internal.SimilarProjectCandidate;
import com.devmozip.backend.dto.ai.UpdateConditionsRequest;

public interface AiRecommendationScoringService {
	Score developer(DeveloperCandidate candidate, UpdateConditionsRequest conditions);
	Score project(SimilarProjectCandidate candidate, UpdateConditionsRequest conditions, String message);
}
