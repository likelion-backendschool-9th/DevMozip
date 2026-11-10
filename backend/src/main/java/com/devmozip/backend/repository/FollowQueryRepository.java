package com.devmozip.backend.repository;

import java.sql.Types;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 팔로워·팔로잉 목록 검색. 직군·기술 스택(JSONB) 검색에 PostgreSQL 함수를 쓰므로 H2(local 프로필)에서는 목록 검색이 동작하지 않는다.
 * 목록과 수에는 회원 상태·태그 조건을 두지 않는다.
 */
@Repository
public class FollowQueryRepository {
	/** 목록 종류. 열 이름은 이 값에서만 정하고 요청 값으로 만들지 않는다. */
	public enum Direction {
		// 대상을 팔로우하는 사람들
		FOLLOWERS("following_id", "follower_id"),
		// 대상이 팔로우하는 사람들
		FOLLOWING("follower_id", "following_id");

		private final String targetColumn;
		private final String listedColumn;

		Direction(String targetColumn, String listedColumn) {
			this.targetColumn = targetColumn;
			this.listedColumn = listedColumn;
		}
	}

	/** 목록의 한 사람과, 요청자가 그 사람을 팔로우하는지·알림 설정 */
	public record Row(long userId, boolean followed, boolean activityNotificationEnabled) {}
	public record Result(List<Row> rows, long total) {}

	private final NamedParameterJdbcTemplate jdbc;

	public FollowQueryRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * @param requesterId 로그인하지 않았으면 null
	 * @param query 앞뒤 공백을 지운 검색어. 없으면 null
	 */
	public Result find(long targetId, Direction direction, Long requesterId, String query, boolean availableOnly, int page, int size) {
		var params = new MapSqlParameterSource("targetId", targetId);
		String from = " FROM follows f JOIN users u ON u.id = f." + direction.listedColumn;
		StringBuilder where = new StringBuilder(" WHERE f.").append(direction.targetColumn).append(" = :targetId");
		if (availableOnly) where.append(" AND u.collaboration_status = 'AVAILABLE'");
		if (query != null) {
			params.addValue("query", "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%");
			where.append(" AND (lower(u.nickname) LIKE :query ESCAPE '!' OR lower(u.tag) LIKE :query ESCAPE '!'")
				.append(" OR ").append(containsInArray("u.job_categories"))
				.append(" OR ").append(containsInArray("u.skills")).append(")");
		}
		Long total = jdbc.queryForObject("SELECT count(*)" + from + where, params, Long.class);

		params.addValue("requesterId", requesterId, Types.BIGINT)
			.addValue("limit", size)
			.addValue("offset", (long) page * size);
		String sql = "SELECT u.id, mine.follower_id IS NOT NULL AS followed,"
			+ " COALESCE(mine.activity_notification_enabled, false) AS activity_notification_enabled"
			+ from + " LEFT JOIN follows mine ON mine.follower_id = :requesterId AND mine.following_id = u.id"
			+ where + " ORDER BY f.created_at DESC, u.id DESC LIMIT :limit OFFSET :offset";
		List<Row> rows = jdbc.query(sql, params, (rs, index) ->
			new Row(rs.getLong("id"), rs.getBoolean("followed"), rs.getBoolean("activity_notification_enabled")));
		return new Result(rows, total == null ? 0 : total);
	}

	// 배열이 아닌 값이 들어 있어도 오류 없이 검색 대상에서 뺀다.
	private static String containsInArray(String column) {
		return "EXISTS (SELECT 1 FROM jsonb_array_elements_text(CASE WHEN jsonb_typeof(" + column + ") = 'array' THEN "
			+ column + " ELSE '[]'::jsonb END) v(value) WHERE lower(v.value) LIKE :query ESCAPE '!')";
	}

	private static String escapeLike(String value) {
		return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
	}
}
