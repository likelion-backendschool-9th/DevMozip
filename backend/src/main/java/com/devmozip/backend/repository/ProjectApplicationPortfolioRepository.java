package com.devmozip.backend.repository;
import java.util.List;
import com.devmozip.backend.entity.ProjectApplicationPortfolio;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectApplicationPortfolioRepository extends JpaRepository<ProjectApplicationPortfolio,Long> {
	List<ProjectApplicationPortfolio> findByApplicationIdOrderByIdAsc(long applicationId);
}
