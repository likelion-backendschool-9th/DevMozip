package com.devmozip.backend.repository;

import java.util.List;
import com.devmozip.backend.entity.ReviewKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewKeywordRepository extends JpaRepository<ReviewKeyword,Long> {
	List<ReviewKeyword> findByActiveTrueOrderByIdAsc();
	List<ReviewKeyword> findByNameInAndActiveTrue(java.util.Collection<String> names);
}
