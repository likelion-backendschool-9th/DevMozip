package com.devmozip.backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 기존 업무 테이블은 읽기만 하고 AI 결과의 detailed_reason만 CAS로 갱신한다. */
@Repository
public class AiPortfolioAnalysisRepository {
	private final JdbcTemplate jdbc;
	public AiPortfolioAnalysisRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
	public record Result(long id, long ownerId, Long developerId, String requestStatus, String conditions, String stored) {}
	public record Account(String role, String status, String tag) {}
	public record Subscription(String status, LocalDateTime startsAt, LocalDateTime periodEnd, LocalDateTime endedAt) {}
	public record Metadata(long id, String title, String summary, String role, String skills, LocalDateTime updatedAt) {}
	public record Body(long id, String description) {}
	public LocalDateTime currentTime() {
		return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", (rs, n) -> LocalDateTime.ofInstant(
			rs.getTimestamp(1).toInstant(), java.time.ZoneId.of("Asia/Seoul")));
	}
	public Optional<Result> result(long id, long owner) {
		return jdbc.query("""
			SELECT r.id,c.user_id,r.recommended_user_id,q.status,q.conditions,r.detailed_reason
			FROM ai_recommendations r JOIN ai_recommendation_requests q ON q.id=r.recommended_request_id
			JOIN ai_conversations c ON c.id=q.ai_conversation_id WHERE r.id=? AND c.user_id=?
			""", (rs, n) -> new Result(rs.getLong(1), rs.getLong(2), rs.getObject(3, Long.class),
				rs.getString(4), rs.getString(5), rs.getString(6)), id, owner).stream().findFirst();
	}
	public Optional<Account> account(long id) {
		return jdbc.query("SELECT role,status,tag FROM users WHERE id=?", (rs, n) ->
			new Account(rs.getString(1), rs.getString(2), rs.getString(3)), id).stream().findFirst();
	}
	public Optional<Subscription> latestSubscription(long userId) {
		return jdbc.query("""
			SELECT status,starts_at,current_period_end,ended_at FROM subscriptions
			WHERE user_id=? ORDER BY starts_at DESC,id DESC LIMIT 1
			""", (rs, n) -> new Subscription(rs.getString(1), rs.getObject(2, LocalDateTime.class),
				rs.getObject(3, LocalDateTime.class), rs.getObject(4, LocalDateTime.class)), userId).stream().findFirst();
	}
	public List<Metadata> metadata(long userId, long afterId) {
		return jdbc.query("""
			SELECT id,title,summary,role,tech_stack,updated_at FROM portfolios
			WHERE user_id=? AND visibility='PUBLIC' AND id>? ORDER BY id LIMIT 200
			""", (rs, n) -> new Metadata(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getString(5), rs.getObject(6, LocalDateTime.class)), userId, afterId);
	}
	public Optional<Body> body(long id, long owner) {
		return jdbc.query("SELECT id,description FROM portfolios WHERE id=? AND user_id=? AND visibility='PUBLIC'",
			(rs, n) -> new Body(rs.getLong(1), rs.getString(2)), id, owner).stream().findFirst();
	}
	public boolean replace(long id, String expected, String replacement) {
		return jdbc.update("""
			UPDATE ai_recommendations SET detailed_reason=? WHERE id=?
			AND (detailed_reason=? OR (detailed_reason IS NULL AND CAST(? AS VARCHAR) IS NULL))
			""", replacement, id, expected, expected) == 1;
	}
}
