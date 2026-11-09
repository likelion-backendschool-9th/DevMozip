package com.devmozip.backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** #48의 지원 처리 코드를 구현하지 않고 기존 청사진 테이블의 대기 여부만 조회한다. */
@Repository
public class ProjectLifecycleRepository {
	private final JdbcTemplate jdbc;
	public ProjectLifecycleRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
	public boolean hasPendingApplications(long projectId) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
			"SELECT EXISTS (SELECT 1 FROM project_applications WHERE project_id=? AND status='PENDING')", Boolean.class, projectId));
	}
}
