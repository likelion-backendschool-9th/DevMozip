package com.devmozip.backend.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 본인 알림 목록과 안 읽음 수. 표준 SQL만 써서 PostgreSQL과 local 프로필의 H2에서 같이 동작한다.
 * 최신순(created_at, id 내림차순)이며, 다음 페이지 여부는 한 건을 더 읽어 정한다.
 *
 * 리뷰 알림의 작성 상태(D4 A)는 조회할 때 reviews를 읽어 계산하며 리뷰 테이블은 바꾸지 않는다.
 * review_id 행의 작성자가 같은 프로젝트에서 가진 행에 PENDING이 없으면 작성 완료, PENDING이 남았고 만료 시각이 지났으면 기간 만료다.
 * 작성 완료·기간 만료(처리 완료)는 읽음 여부와 관계없이 안 읽음에서 빠진다. reviews 스키마가 바뀌면 이 SQL도 함께 고친다.
 */
@Repository
public class NotificationQueryRepository {
	public enum Filter { ALL, UNREAD, READ }

	/** reviewId·pendingReviews·reviewExpiresAt은 리뷰 알림에만 있다. */
	public record Row(long id, String type, String title, String body, String actionUrl, Long projectId,
		LocalDateTime readAt, LocalDateTime createdAt, Long reviewId, Long pendingReviews, LocalDateTime reviewExpiresAt) {}

	public record Slice(List<Row> rows, boolean hasNext) {}

	private static final String SOURCE = " FROM notifications n LEFT JOIN reviews r ON r.id = n.review_id"
		+ " WHERE n.recipient_id = :recipientId";
	private static final String SAME_REVIEWER = " FROM reviews p WHERE p.project_id = r.project_id"
		+ " AND p.reviewer_member_id = r.reviewer_member_id AND p.status = 'PENDING'";
	private static final String RESOLVED = "(r.id IS NOT NULL AND (r.expires_at <= :now OR NOT EXISTS (SELECT 1" + SAME_REVIEWER + ")))";
	private static final String UNREAD = "(n.read_at IS NULL AND NOT " + RESOLVED + ")";

	private final NamedParameterJdbcTemplate jdbc;

	public NotificationQueryRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Slice find(long recipientId, Filter filter, int page, int size, LocalDateTime now) {
		var params = new MapSqlParameterSource("recipientId", recipientId)
			.addValue("now", now)
			.addValue("limit", size + 1)
			.addValue("offset", (long) page * size);
		String sql = "SELECT n.id, n.type, n.title, n.body, n.action_url, n.project_id, n.read_at, n.created_at,"
			+ " r.id AS review_id, r.expires_at AS review_expires_at,"
			+ " CASE WHEN r.id IS NULL THEN NULL ELSE (SELECT count(*)" + SAME_REVIEWER + ") END AS pending_reviews"
			+ SOURCE + condition(filter)
			+ " ORDER BY n.created_at DESC, n.id DESC LIMIT :limit OFFSET :offset";
		List<Row> rows = jdbc.query(sql, params, (rs, index) -> row(rs));
		boolean hasNext = rows.size() > size;
		return new Slice(hasNext ? rows.subList(0, size) : rows, hasNext);
	}

	/** 탭·페이지와 관계없는 전체 안 읽음 수. 처리 완료된 리뷰 알림은 세지 않는다. */
	public long countUnread(long recipientId, LocalDateTime now) {
		Long count = jdbc.queryForObject("SELECT count(*)" + SOURCE + " AND " + UNREAD,
			new MapSqlParameterSource("recipientId", recipientId).addValue("now", now), Long.class);
		return count == null ? 0 : count;
	}

	/**
	 * 리뷰 알림에 저장할 review_id. 작성자 한 명은 한 프로젝트에서 대상 수만큼 reviews 행을 가지므로 그중 가장 작은 id다.
	 * 리뷰 이벤트에 리뷰 ID가 없어 알림 쪽이 조회한다.
	 */
	public Optional<Long> findReviewAnchor(long projectId, long reviewerUserId) {
		Long id = jdbc.queryForObject("SELECT min(r.id) FROM reviews r JOIN project_members m ON m.id = r.reviewer_member_id"
			+ " WHERE r.project_id = :projectId AND m.user_id = :userId",
			new MapSqlParameterSource("projectId", projectId).addValue("userId", reviewerUserId), Long.class);
		return Optional.ofNullable(id);
	}

	private String condition(Filter filter) {
		return switch (filter) {
			case ALL -> "";
			case UNREAD -> " AND " + UNREAD;
			case READ -> " AND NOT " + UNREAD;
		};
	}

	private Row row(ResultSet rs) throws SQLException {
		return new Row(rs.getLong("id"), rs.getString("type"), rs.getString("title"), rs.getString("body"),
			rs.getString("action_url"), rs.getObject("project_id", Long.class),
			rs.getObject("read_at", LocalDateTime.class), rs.getObject("created_at", LocalDateTime.class),
			rs.getObject("review_id", Long.class), rs.getObject("pending_reviews", Long.class),
			rs.getObject("review_expires_at", LocalDateTime.class));
	}
}
