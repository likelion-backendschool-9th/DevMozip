package com.devmozip.backend.repository;

import java.util.List;
import java.util.Optional;
import com.devmozip.backend.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
	boolean existsByProjectIdAndUserId(long projectId,long userId);
	Optional<ProjectMember> findByProjectIdAndUserIdAndStatus(long projectId, long userId, ProjectMember.Status status);
	long countByProjectIdAndStatusAndEndRequestedAtIsNotNull(long projectId, ProjectMember.Status status);
	List<ProjectMember> findByProjectId(long projectId);
	boolean existsByProjectIdAndUserIdAndStatus(long projectId, long userId, ProjectMember.Status status);
	long countByProjectIdAndStatus(long projectId, ProjectMember.Status status);
}
