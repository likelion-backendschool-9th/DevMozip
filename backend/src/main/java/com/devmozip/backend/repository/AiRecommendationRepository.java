package com.devmozip.backend.repository;

import com.devmozip.backend.entity.AiRecommendation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRecommendationRepository extends JpaRepository<AiRecommendation, Long> {
	List<AiRecommendation> findByRequestIdOrderByRankAscIdAsc(long requestId);
}
