package com.devmozip.backend.repository;

import java.text.Normalizer;
import com.devmozip.backend.service.ProjectRoleValues;
import java.time.LocalDate;
import com.devmozip.backend.entity.enums.ProjectStatus;
import java.util.*;
import com.devmozip.backend.dto.project.MyProjectsTab;
import com.devmozip.backend.dto.project.ProjectSearchRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JSONB 필터와 페이지 집계는 DB에서 처리하며 공고 전체를 메모리로 가져오지 않는다. */
@Repository
public class ProjectQueryRepository {
	private final NamedParameterJdbcTemplate jdbc;
	public ProjectQueryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
	public record Row(long id, long memberCount, String myRole) {}
	public record Result(List<Row> rows, long total) {}

	public Result search(ProjectSearchRequest request, LocalDate today, Long userId, MyProjectsTab tab) {
		StringBuilder where = new StringBuilder(" WHERE p.deleted_at IS NULL");
		MapSqlParameterSource params = new MapSqlParameterSource("today", today);
		if (userId == null) where.append(" AND p.is_public = true");
		else {
			params.addValue("userId", userId);
			String joined="EXISTS (SELECT 1 FROM project_members mine WHERE mine.project_id=p.id AND mine.user_id=:userId AND mine.status='ACTIVE')";
			String pending="EXISTS (SELECT 1 FROM project_applications a WHERE a.project_id=p.id AND a.applicant_member_id=:userId AND a.status='PENDING')";
			where.append(" AND ").append(tab==MyProjectsTab.APPLYING?pending:tab==MyProjectsTab.ALL?"("+joined+" OR "+pending+")":joined);
			if (tab == MyProjectsTab.HOSTED) where.append(" AND p.host_member_id=:userId");
			if (tab == MyProjectsTab.PARTICIPATING) where.append(" AND p.host_member_id<>:userId AND p.status<>'COMPLETED'");
			if (tab == MyProjectsTab.COMPLETED) where.append(" AND p.status='COMPLETED'");
		}
		if (request.query() != null && !request.query().isBlank()) {
			String escaped = normalize(request.query()).replace("!", "!!").replace("%", "!%").replace("_", "!_");
			params.addValue("query", "%" + escaped + "%");
			where.append(" AND (lower(p.title) LIKE :query ESCAPE '!' OR EXISTS (SELECT 1 FROM jsonb_array_elements_text(p.tech_stacks) t(value) WHERE lower(t.value) LIKE :query ESCAPE '!'))");
		}
		if (request.jobRole() != null && !request.jobRole().isBlank()) {
			params.addValue("jobRole", ProjectRoleValues.key(request.jobRole()));
			where.append(" AND EXISTS (SELECT 1 FROM jsonb_array_elements(p.recruitment_slots) s(value) WHERE lower(s.value->>'roleCode')=:jobRole)");
		}
		if (request.status() != null) {
			params.addValue("status", request.status().name());
			where.append(" AND p.status=:status");
			if (request.status() == ProjectStatus.RECRUITING) where.append(" AND p.recruitment_end>=:today");
		}
		if (request.interests() != null && !request.interests().isEmpty()) {
			params.addValue("interests", request.interests().stream().map(this::normalize).toList());
			where.append(" AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(p.interests) i(value) WHERE lower(i.value) IN (:interests))");
		}
		if (request.techStacks() != null) {
			for (int i=0; i<request.techStacks().size(); i++) {
				String key = "stack" + i;
				params.addValue(key, normalize(request.techStacks().get(i)));
				where.append(" AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(p.tech_stacks) t(value) WHERE lower(t.value)=:").append(key).append(")");
			}
		}
		if (request.effectiveSort() == ProjectSearchRequest.Sort.DEADLINE)
			where.append(" AND p.status='RECRUITING' AND p.recruitment_end>=:today");
		String order = switch (request.effectiveSort()) {
			case LATEST -> "p.created_at DESC, p.id DESC";
			case DEADLINE -> "p.recruitment_end ASC, p.created_at DESC, p.id DESC";
			case POPULAR -> "p.view_count DESC, p.created_at DESC, p.id DESC";
		};
		Long total = jdbc.queryForObject("SELECT count(*) FROM projects p" + where, params, Long.class);
		params.addValue("limit", request.effectiveSize()).addValue("offset", (long)request.effectivePage()*request.effectiveSize());
		String role = userId == null ? "NULL" : "(SELECT mine.role FROM project_members mine WHERE mine.project_id=p.id AND mine.user_id=:userId AND mine.status='ACTIVE')";
		String sql = "SELECT p.id, (SELECT count(*) FROM project_members m WHERE m.project_id=p.id AND m.status='ACTIVE') AS member_count, "
			+ role + " AS my_role FROM projects p" + where + " ORDER BY " + order + " LIMIT :limit OFFSET :offset";
		return new Result(jdbc.query(sql, params, (rs, row) -> new Row(rs.getLong("id"), rs.getLong("member_count"), rs.getString("my_role"))), total == null ? 0 : total);
	}
	public com.devmozip.backend.dto.project.ProjectDetailResponse.Host host(long userId) {
		var result=jdbc.query("SELECT id,tag,nickname FROM users WHERE id=:userId AND (status='ACTIVE' OR (status IS NULL AND email IS NULL))",new MapSqlParameterSource("userId",userId),
			(rs,row)->new com.devmozip.backend.dto.project.ProjectDetailResponse.Host(rs.getLong("id"),rs.getString("tag"),rs.getString("nickname")));
		return result.isEmpty()?null:result.getFirst();
	}
	/** 공고를 열람할 수 있는 사용자에게 현재 팀원의 공개 프로필 식별 정보만 제공한다. */
	public List<com.devmozip.backend.dto.project.ProjectDetailResponse.TeamMember> team(long projectId) {
		return jdbc.query("SELECT m.user_id, CASE WHEN u.status='ACTIVE' THEN u.tag ELSE NULL END AS tag, u.nickname, m.role, m.job_role "
			+ "FROM project_members m LEFT JOIN users u ON u.id=m.user_id WHERE m.project_id=:id AND m.status='ACTIVE' "
			+ "ORDER BY CASE WHEN m.role='HOST' THEN 0 ELSE 1 END, m.joined_at, m.id",new MapSqlParameterSource("id",projectId),
			(rs,row)->new com.devmozip.backend.dto.project.ProjectDetailResponse.TeamMember(rs.getLong("user_id"),rs.getString("tag"),rs.getString("nickname"),rs.getString("role"),rs.getString("job_role")));
	}

	private String normalize(String value) { return Normalizer.normalize(value, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT); }
}
