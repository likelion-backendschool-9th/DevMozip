package com.devmozip.backend.repository;

import com.devmozip.backend.entity.AiRecommendationRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRecommendationRequestRepository extends JpaRepository<AiRecommendationRequest, Long> {
	Optional<AiRecommendationRequest> findByConversationIdAndIdempotencyKey(long conversationId, String key);
	Optional<AiRecommendationRequest> findByIdAndConversationId(long id, long conversationId);
	Optional<AiRecommendationRequest> findFirstByConversationIdOrderByCreatedAtDescIdDesc(long conversationId);
}
