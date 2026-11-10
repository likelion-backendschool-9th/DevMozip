package com.devmozip.backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 프로젝트 시작 전 대기 지원서 존재 여부를 조회한다. */
@Repository
public class ProjectLifecycleRepository {
	private final JdbcTemplate jdbc;
	public ProjectLifecycleRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
	public boolean hasPendingApplications(long projectId) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
			"SELECT EXISTS (SELECT 1 FROM project_applications WHERE project_id=? AND status='PENDING')", Boolean.class, projectId));
	}
}
