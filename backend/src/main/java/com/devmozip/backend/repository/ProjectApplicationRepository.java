package com.devmozip.backend.repository;

import java.util.List;
import java.util.Optional;
import com.devmozip.backend.entity.ProjectApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectApplicationRepository extends JpaRepository<ProjectApplication,Long> {
	@org.springframework.data.jpa.repository.Query("select a.applicantMemberId from ProjectApplication a where a.id=:id and a.project.id=:projectId")
	Optional<Long> findApplicantId(@org.springframework.data.repository.query.Param("id") long id, @org.springframework.data.repository.query.Param("projectId") long projectId);
	boolean existsByProjectIdAndApplicantMemberId(long projectId,long userId);
	Optional<ProjectApplication> findByIdAndProjectId(long id,long projectId);
	Optional<ProjectApplication> findByProjectIdAndApplicantMemberId(long projectId,long userId);
	List<ProjectApplication> findByProjectIdOrderByAppliedAtDescIdDesc(long projectId);
}
