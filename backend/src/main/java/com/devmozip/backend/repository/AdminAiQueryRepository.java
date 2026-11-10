package com.devmozip.backend.repository;

import java.util.List;
import com.devmozip.backend.dto.admin.AdminAiFailureResponse;
import com.devmozip.backend.dto.admin.AdminAiUsageResponse;
import com.devmozip.backend.dto.admin.AdminAiUsageSummary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 관리자 AI 사용량·오류 조회 (WBS 14.10·14.11, docs/admin-plan.md D6). AI 담당 테이블을 읽기만 한다.
 * ai_usages·ai_recommendation_requests·ai_conversations의 회원 칼럼은 연관관계가 아니라 ID라서 JDBC로 users와 잇는다.
 * 추천 조건(conditions)과 대화·메시지 내용은 읽지 않는다.
 */
@Repository
public class AdminAiQueryRepository {
	private static final String WITHDRAWN = "(m.email IS NULL AND m.status IS NULL)";
	private final NamedParameterJdbcTemplate jdbc;

	public AdminAiQueryRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public AdminAiUsageSummary usageSummary(String month) {
		return jdbc.queryForObject("""
			SELECT COALESCE(SUM(used_count), 0) AS used, COALESCE(SUM(pending_count), 0) AS pending, COUNT(*) AS members
			FROM ai_usages WHERE usage_month = :month""", new MapSqlParameterSource("month", month),
			(rs, i) -> new AdminAiUsageSummary(month, rs.getLong("used"), rs.getLong("pending"), rs.getLong("members")));
	}

	/** 그 달의 회원별 사용량. 사용 횟수가 많은 순, 같으면 회원 ID순이다. */
	public List<AdminAiUsageResponse> usage(String month, int page, int size) {
		var params = new MapSqlParameterSource("month", month).addValue("limit", size).addValue("offset", (long) page * size);
		return jdbc.query("SELECT u.user_id, m.tag, m.nickname, m.role, " + WITHDRAWN + " AS withdrawn, u.used_count, u.pending_count"
			+ " FROM ai_usages u LEFT JOIN users m ON m.id = u.user_id WHERE u.usage_month = :month"
			+ " ORDER BY u.used_count DESC, u.user_id LIMIT :limit OFFSET :offset", params,
			(rs, i) -> new AdminAiUsageResponse(rs.getLong("user_id"), blankToNull(rs.getString("tag")), rs.getString("nickname"),
				rs.getString("role"), rs.getBoolean("withdrawn"), rs.getInt("used_count"), rs.getInt("pending_count")));
	}

	public long failureCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM ai_recommendation_requests WHERE status = 'FAILED'", new MapSqlParameterSource(), Long.class);
	}

	/** 실패한 추천 요청. 최신 요청 ID순이다. */
	public List<AdminAiFailureResponse> failures(int page, int size) {
		var params = new MapSqlParameterSource("limit", size).addValue("offset", (long) page * size);
		return jdbc.query("SELECT r.id, r.ai_conversation_id, c.user_id, m.tag, m.nickname, " + WITHDRAWN + " AS withdrawn, r.status,"
			+ " r.created_at, r.completed_at FROM ai_recommendation_requests r JOIN ai_conversations c ON c.id = r.ai_conversation_id"
			+ " LEFT JOIN users m ON m.id = c.user_id WHERE r.status = 'FAILED' ORDER BY r.id DESC LIMIT :limit OFFSET :offset", params,
			(rs, i) -> new AdminAiFailureResponse(rs.getLong("id"), rs.getLong("ai_conversation_id"), rs.getObject("user_id", Long.class),
				blankToNull(rs.getString("tag")), rs.getString("nickname"), rs.getBoolean("withdrawn"), rs.getString("status"),
				rs.getObject("created_at", java.time.LocalDateTime.class), rs.getObject("completed_at", java.time.LocalDateTime.class)));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
