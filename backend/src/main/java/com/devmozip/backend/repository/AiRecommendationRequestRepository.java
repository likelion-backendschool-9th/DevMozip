package com.devmozip.backend.repository;

import com.devmozip.backend.entity.AiRecommendationRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRecommendationRequestRepository extends JpaRepository<AiRecommendationRequest, Long> {
	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	java.util.List<AiRecommendationRequest> findByUsage_IdAndStatusOrderByIdAsc(long usageId, AiRecommendationRequest.Status status);
	java.util.List<AiRecommendationRequest> findByConversationIdOrderByCreatedAtDescIdDesc(long conversationId);
	Optional<AiRecommendationRequest> findByConversationIdAndIdempotencyKey(long conversationId, String key);
	Optional<AiRecommendationRequest> findByIdAndConversationId(long id, long conversationId);
	Optional<AiRecommendationRequest> findFirstByConversationIdOrderByCreatedAtDescIdDesc(long conversationId);
}
