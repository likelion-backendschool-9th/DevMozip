package com.devmozip.backend.repository;

import java.util.List;
import java.util.Optional;
import com.devmozip.backend.entity.ProjectApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectApplicationRepository extends JpaRepository<ProjectApplication,Long> {
	boolean existsByProjectIdAndApplicantMemberId(long projectId,long userId);
	Optional<ProjectApplication> findByIdAndProjectId(long id,long projectId);
	Optional<ProjectApplication> findByProjectIdAndApplicantMemberId(long projectId,long userId);
	List<ProjectApplication> findByProjectIdOrderByAppliedAtDescIdDesc(long projectId);
}
