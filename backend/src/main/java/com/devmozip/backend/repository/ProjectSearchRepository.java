package com.devmozip.backend.repository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import com.devmozip.backend.dto.search.ProjectSearchStatus;
import com.devmozip.backend.entity.enums.ProjectJobRole;
import com.devmozip.backend.service.SearchKeywords;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 프로젝트 탐색 전용 조회(WBS 6.3). 7.1 목록 조회(ProjectQueryRepository)를 쓰지 않고 projects·project_members를 읽기만 한다.
 * 공개 범위는 7.1 비로그인 목록과 같다: 삭제되지 않았고 공개(is_public)인 공고만 나온다. 프로젝트 쪽 규칙이 바뀌면 함께 맞춘다.
 * JSONB 함수를 쓰므로 H2(local 프로필)에서는 동작하지 않는다.
 */
@Repository
public class ProjectSearchRepository {
	/**
	 * @param tokens 정리한 검색 단어(SearchKeywords.tokens). 모두 어딘가에 포함되어야 한다
	 * @param jobRoleKey ProjectRoleValues.key로 바꾼 모집 직군. 없으면 null
	 * @param interestKeys·techStackKeys SearchKeywords.normalize로 정리한 값
	 */
	public record Criteria(List<String> tokens, String jobRoleKey, ProjectSearchStatus status,
		List<String> interestKeys, List<String> techStackKeys) {}

	public record Row(long id, long memberCount) {}
	public record Result(List<Row> rows, long total) {}

	private static final List<String> BASE_ROLE_CODES = Arrays.stream(ProjectJobRole.values()).map(Enum::name).toList();

	private final NamedParameterJdbcTemplate jdbc;

	public ProjectSearchRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** @param today 모집 마감 여부를 가르는 오늘 날짜(Asia/Seoul) */
	public Result search(Criteria criteria, LocalDate today, int page, int size) {
		var params = new MapSqlParameterSource("today", today).addValue("baseRoles", BASE_ROLE_CODES);
		StringBuilder where = new StringBuilder(" WHERE p.deleted_at IS NULL AND p.is_public = true");

		for (int i = 0; i < criteria.tokens().size(); i++) {
			String token = criteria.tokens().get(i);
			String key = "token" + i;
			params.addValue(key, SearchKeywords.containsPattern(token));
			String like = " LIKE :" + key + " ESCAPE '!'";
			// 기본 직군은 코드로 저장되므로 라벨(예: 백엔드)에 검색 단어가 들어 있으면 그 코드로 찾고,
			// 직접 입력한 직군은 저장된 문구로 찾는다.
			StringBuilder role = new StringBuilder("(s.value->>'roleCode') NOT IN (:baseRoles) AND lower(s.value->>'roleCode')").append(like);
			List<String> codes = roleCodesWithLabel(token);
			if (!codes.isEmpty()) {
				params.addValue("roles" + i, codes);
				role.insert(0, "(s.value->>'roleCode') IN (:roles" + i + ") OR (").append(")");
			}
			where.append(" AND (lower(p.title)").append(like)
				.append(" OR ").append(anyText("p.interests", "lower(v.value)" + like))
				.append(" OR ").append(anyText("p.tech_stacks", "lower(v.value)" + like))
				.append(" OR ").append(anySlot(role.toString()))
				.append(")");
		}
		if (criteria.jobRoleKey() != null) {
			params.addValue("jobRole", criteria.jobRoleKey());
			where.append(" AND ").append(anySlot("lower(s.value->>'roleCode') = :jobRole"));
		}
		if (criteria.status() != null) {
			where.append(switch (criteria.status()) {
				case RECRUITING -> " AND p.status = 'RECRUITING' AND p.recruitment_end >= :today";
				case RECRUITMENT_CLOSED -> " AND p.status = 'RECRUITING' AND p.recruitment_end < :today";
				case RECRUITMENT_COMPLETED -> " AND p.status = 'RECRUITMENT_COMPLETED'";
				case IN_PROGRESS -> " AND p.status = 'IN_PROGRESS'";
				case COMPLETED -> " AND p.status = 'COMPLETED'";
			});
		}
		if (!criteria.interestKeys().isEmpty()) {
			params.addValue("interests", criteria.interestKeys());
			where.append(" AND ").append(anyText("p.interests", "lower(v.value) IN (:interests)"));
		}
		for (int i = 0; i < criteria.techStackKeys().size(); i++) {
			String key = "stack" + i;
			params.addValue(key, criteria.techStackKeys().get(i));
			where.append(" AND ").append(anyText("p.tech_stacks", "lower(v.value) = :" + key));
		}

		Long total = jdbc.queryForObject("SELECT count(*) FROM projects p" + where, params, Long.class);
		params.addValue("limit", size).addValue("offset", (long) page * size);
		String sql = "SELECT p.id, (SELECT count(*) FROM project_members m WHERE m.project_id = p.id AND m.status = 'ACTIVE') AS member_count"
			+ " FROM projects p" + where + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit OFFSET :offset";
		List<Row> rows = jdbc.query(sql, params, (rs, index) -> new Row(rs.getLong("id"), rs.getLong("member_count")));
		return new Result(rows, total == null ? 0 : total);
	}

	private static List<String> roleCodesWithLabel(String token) {
		return Arrays.stream(ProjectJobRole.values())
			.filter(role -> role.getLabel().toLowerCase(Locale.ROOT).contains(token))
			.map(Enum::name)
			.toList();
	}

	private static String anyText(String column, String condition) {
		return "EXISTS (SELECT 1 FROM jsonb_array_elements_text(" + array(column) + ") v(value) WHERE " + condition + ")";
	}

	private static String anySlot(String condition) {
		return "EXISTS (SELECT 1 FROM jsonb_array_elements(" + array("p.recruitment_slots") + ") s(value) WHERE " + condition + ")";
	}

	// 배열이 아닌 값이 들어 있어도 오류 없이 조건에서 뺀다.
	private static String array(String column) {
		return "CASE WHEN jsonb_typeof(" + column + ") = 'array' THEN " + column + " ELSE '[]'::jsonb END";
	}
}
