package com.devmozip.backend.repository;

import java.util.List;
import com.devmozip.backend.service.SearchKeywords;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 개발자 검색(WBS 6.2·6.5). 조건·수·페이지를 DB에서 처리하며 회원 전체를 메모리로 읽지 않는다.
 * JSONB 함수와 ICU 정렬 규칙을 쓰므로 H2(local 프로필)에서는 동작하지 않는다.
 *
 * 검색 대상은 3.1 프로필 조회(ProfileService.findBySlug)가 찾을 수 있는 회원과 같다: ACTIVE이고 태그와 직군이 있다.
 */
@Repository
public class DeveloperSearchRepository {
	/**
	 * @param tokens 정리한 검색 단어(SearchKeywords.tokens). 모두 어딘가에 포함되어야 한다
	 * @param available null이면 협업 상태로 거르지 않는다
	 * @param following true면 requesterId가 팔로우한 회원만. 호출하는 쪽에서 로그인을 확인한다
	 */
	public record Criteria(List<String> tokens, String jobCategory, String career, List<String> interests,
		List<String> skills, Boolean available, boolean following) {}

	/** 결과의 한 회원과, 요청자가 그 회원을 팔로우하는지 */
	public record Row(long id, boolean followed) {}
	public record Result(List<Row> rows, long total) {}

	private final NamedParameterJdbcTemplate jdbc;

	public DeveloperSearchRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** @param requesterId 로그인하지 않았으면 null */
	public Result search(Criteria criteria, Long requesterId, int page, int size) {
		var params = new MapSqlParameterSource();
		StringBuilder where = new StringBuilder(" WHERE u.status = 'ACTIVE' AND u.tag IS NOT NULL AND u.tag <> ''")
			.append(" AND jsonb_typeof(u.job_categories) = 'array' AND jsonb_array_length(u.job_categories) > 0");

		for (int i = 0; i < criteria.tokens().size(); i++) {
			String key = "token" + i;
			params.addValue(key, SearchKeywords.containsPattern(criteria.tokens().get(i)));
			String like = " LIKE :" + key + " ESCAPE '!'";
			where.append(" AND (lower(u.nickname)").append(like)
				.append(" OR lower(u.tag)").append(like)
				.append(" OR lower(u.career)").append(like)
				.append(" OR ").append(anyElement("u.job_categories", "lower(v.value)" + like))
				.append(" OR ").append(anyElement("u.skills", "lower(v.value)" + like))
				.append(" OR ").append(anyElement("u.interests", "lower(v.value)" + like))
				.append(")");
		}
		if (criteria.jobCategory() != null) {
			params.addValue("jobCategory", criteria.jobCategory());
			where.append(" AND ").append(anyElement("u.job_categories", "v.value = :jobCategory"));
		}
		if (criteria.career() != null) {
			params.addValue("career", criteria.career());
			where.append(" AND u.career = :career");
		}
		if (!criteria.interests().isEmpty()) {
			params.addValue("interests", criteria.interests());
			where.append(" AND ").append(anyElement("u.interests", "v.value IN (:interests)"));
		}
		for (int i = 0; i < criteria.skills().size(); i++) {
			String key = "skill" + i;
			params.addValue(key, criteria.skills().get(i));
			where.append(" AND ").append(anyElement("u.skills", "v.value = :" + key));
		}
		if (criteria.available() != null) {
			where.append(criteria.available()
				? " AND u.collaboration_status = 'AVAILABLE'"
				: " AND (u.collaboration_status IS NULL OR u.collaboration_status <> 'AVAILABLE')");
		}
		if (requesterId != null) params.addValue("requesterId", requesterId);
		if (criteria.following()) {
			if (requesterId == null) throw new IllegalArgumentException("팔로잉 검색에는 요청자가 필요합니다.");
			where.append(" AND EXISTS (SELECT 1 FROM follows f WHERE f.follower_id = :requesterId AND f.following_id = u.id)");
		}

		Long total = jdbc.queryForObject("SELECT count(*) FROM users u" + where, params, Long.class);
		params.addValue("limit", size).addValue("offset", (long) page * size);
		String followed = requesterId == null ? "false"
			: "EXISTS (SELECT 1 FROM follows mine WHERE mine.follower_id = :requesterId AND mine.following_id = u.id)";
		// 목업의 한국어 기준 닉네임 순(localeCompare 'ko')과 같도록 ICU 한국어 정렬 규칙을 쓴다.
		String sql = "SELECT u.id, " + followed + " AS followed FROM users u" + where
			+ " ORDER BY u.nickname COLLATE \"ko-x-icu\", u.id LIMIT :limit OFFSET :offset";
		List<Row> rows = jdbc.query(sql, params, (rs, index) -> new Row(rs.getLong("id"), rs.getBoolean("followed")));
		return new Result(rows, total == null ? 0 : total);
	}

	// 배열이 아닌 값이 들어 있어도 오류 없이 조건에서 뺀다(FollowQueryRepository와 같다).
	private static String anyElement(String column, String condition) {
		return "EXISTS (SELECT 1 FROM jsonb_array_elements_text(CASE WHEN jsonb_typeof(" + column + ") = 'array' THEN "
			+ column + " ELSE '[]'::jsonb END) v(value) WHERE " + condition + ")";
	}
}
