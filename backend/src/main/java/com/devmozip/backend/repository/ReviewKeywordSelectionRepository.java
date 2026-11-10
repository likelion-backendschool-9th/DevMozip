package com.devmozip.backend.repository;

import com.devmozip.backend.entity.ReviewKeywordSelection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewKeywordSelectionRepository extends JpaRepository<ReviewKeywordSelection,Long> {
	@org.springframework.data.jpa.repository.Query("select s from ReviewKeywordSelection s join fetch s.keyword where s.review.id in :reviewIds order by s.id")
	java.util.List<ReviewKeywordSelection> findWithKeywordsByReviewIds(@org.springframework.data.repository.query.Param("reviewIds") java.util.Collection<Long> reviewIds);
}
