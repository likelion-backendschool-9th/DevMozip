package com.devmozip.backend.repository;

import com.devmozip.backend.entity.AiUsage;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {
	Optional<AiUsage> findByUserIdAndUsageMonth(long userId, String month);
	List<AiUsage> findByUserIdOrderByIdAsc(long userId);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from AiUsage u where u.id = :id")
	Optional<AiUsage> lock(@Param("id") long id);
}
