package com.devmozip.backend.repository;

import java.util.List;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import com.devmozip.backend.entity.ProjectApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectApplicationRepository extends JpaRepository<ProjectApplication,Long> {
	boolean existsByProjectIdAndApplicantMemberId(long projectId,long userId);
	Optional<ProjectApplication> findByIdAndProjectId(long id,long projectId);
	Optional<ProjectApplication> findByProjectIdAndApplicantMemberId(long projectId,long userId);
	List<ProjectApplication> findByProjectIdOrderByAppliedAtDescIdDesc(long projectId);
	// 회원 행에서 원자적으로 차감해 서로 다른 프로젝트의 동시 취소도 감점을 덮어쓰지 않는다.
	@Modifying(flushAutomatically = true)
	@Query(value = "UPDATE users SET trust_score=GREATEST(COALESCE(trust_score,36.5)-:amount,0) WHERE id=:userId", nativeQuery = true)
	int deductCancellationPenalty(@Param("userId") long userId, @Param("amount") BigDecimal amount);
}
