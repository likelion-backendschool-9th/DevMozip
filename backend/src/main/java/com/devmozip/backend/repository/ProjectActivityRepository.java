package com.devmozip.backend.repository;

import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.entity.enums.ProjectStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectActivityRepository {
	private final NamedParameterJdbcTemplate jdbc;
	public ProjectActivityRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

	public ProjectActivityPageResponse find(long targetId, Long viewerId, int page, int size) {
		var params = new MapSqlParameterSource("target", targetId).addValue("viewer", viewerId);
		String from = " FROM projects p JOIN project_members m ON m.project_id=p.id WHERE m.user_id=:target AND p.deleted_at IS NULL";
		// 과거 참여 이력은 보존하되 비공개 열람 권한은 현재 팀원 여부로 판단한다.
		from += " AND (p.is_public=true OR EXISTS (SELECT 1 FROM project_members v WHERE v.project_id=p.id AND v.user_id=:viewer AND v.status='ACTIVE'))";
		long total = jdbc.queryForObject("SELECT count(*)" + from, params, Long.class);
		params.addValue("limit", size).addValue("offset", (long) page * size);
		var items = jdbc.query("SELECT p.id,p.title,p.image_url,p.status,p.development_start,p.development_end,p.completed_at,m.role,m.job_role,m.status AS membership_status"
			+ from + " ORDER BY p.development_start DESC,p.id DESC LIMIT :limit OFFSET :offset", params,
			(rs, row) -> new ProjectActivityResponse(rs.getLong("id"), rs.getString("title"), rs.getString("image_url"), ProjectStatus.valueOf(rs.getString("status")),
				rs.getObject("development_start", java.time.LocalDate.class), rs.getObject("development_end", java.time.LocalDate.class),
				rs.getObject("completed_at", java.time.LocalDateTime.class), rs.getString("role"), rs.getString("job_role"), rs.getString("membership_status")));
		return new ProjectActivityPageResponse(items, total, page, size, (total + size - 1) / size);
	}
}
