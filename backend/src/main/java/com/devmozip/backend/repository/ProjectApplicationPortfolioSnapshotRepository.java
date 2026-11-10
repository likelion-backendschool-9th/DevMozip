package com.devmozip.backend.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.devmozip.backend.entity.ProjectApplicationPortfolioSnapshot;
import com.devmozip.backend.dto.project.ProjectApplicationPortfolioResponse;

public interface ProjectApplicationPortfolioSnapshotRepository extends JpaRepository<ProjectApplicationPortfolioSnapshot,Long> {
	@Query("select new com.devmozip.backend.dto.project.ProjectApplicationPortfolioResponse(s.id,s.sourcePortfolioId,s.title) from ProjectApplicationPortfolioSnapshot s where s.submission.application.id=:applicationId order by s.id")
	List<ProjectApplicationPortfolioResponse> summaries(long applicationId);
}
