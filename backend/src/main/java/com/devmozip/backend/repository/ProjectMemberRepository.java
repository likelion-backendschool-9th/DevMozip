package com.devmozip.backend.repository;

import com.devmozip.backend.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
	boolean existsByProjectIdAndUserIdAndStatus(long projectId, long userId, ProjectMember.Status status);
	long countByProjectIdAndStatus(long projectId, ProjectMember.Status status);
}
