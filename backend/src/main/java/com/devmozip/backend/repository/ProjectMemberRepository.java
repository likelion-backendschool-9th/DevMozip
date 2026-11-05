package com.devmozip.backend.repository;

import com.devmozip.backend.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
	java.util.List<ProjectMember> findByProjectId(long projectId);
	boolean existsByProjectIdAndUserIdAndStatus(long projectId, long userId, ProjectMember.Status status);
}
