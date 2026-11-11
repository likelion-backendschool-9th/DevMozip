package com.devmozip.backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 탈퇴에 한정한 기존 테이블 정리. 스키마나 엔티티 매핑은 변경하지 않는다. */
@Repository
public class AccountWithdrawalRepository {
	private final JdbcTemplate jdbc;
	public AccountWithdrawalRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	public record HostedProject(long id, String title) {}

	public List<HostedProject> unfinishedHosted(long userId) {
		return jdbc.query("SELECT id,title FROM projects WHERE host_member_id=? AND deleted_at IS NULL AND status<>'COMPLETED' ORDER BY id",
			(rs, row) -> new HostedProject(rs.getLong(1), rs.getString(2)), userId);
	}

	/** 구독을 잠가 결제 스케줄러의 ACTIVE → RENEWING 전환과 직렬화한다. */
	public boolean lockSubscriptions(long userId) {
		return jdbc.query("SELECT status FROM subscriptions WHERE user_id=? ORDER BY id FOR UPDATE",
			(rs, row) -> rs.getString(1), userId).contains("RENEWING");
	}

	public boolean isWithdrawn(long userId) {
		// 준비 작업을 본 다른 서버는 진행 중인 탈퇴 트랜잭션의 커밋/롤백을 기다려야 한다.
		return jdbc.query("SELECT (email IS NULL AND status IS NULL) FROM users WHERE id=? FOR UPDATE",
			(rs, row) -> rs.getBoolean(1), userId).stream().findFirst().orElse(true);
	}

	public List<Long> participatingProjects(long userId) {
		return jdbc.query("""
			SELECT DISTINCT p.id FROM projects p WHERE p.deleted_at IS NULL AND p.status<>'COMPLETED'
			AND (EXISTS(SELECT 1 FROM project_members m WHERE m.project_id=p.id AND m.user_id=? AND m.status='ACTIVE')
			OR EXISTS(SELECT 1 FROM project_applications a WHERE a.project_id=p.id AND a.applicant_member_id=? AND a.status='PENDING')) ORDER BY p.id
			""", (rs, row) -> rs.getLong(1), userId, userId);
	}

	public void cancelApplications(long projectId, long userId, LocalDateTime now) {
		jdbc.update("UPDATE project_applications SET status='CANCELLED',cancelled_at=?,updated_at=? WHERE project_id=? AND applicant_member_id=? AND status IN ('PENDING','APPROVED')", now, now, projectId, userId);
	}

	public void erasePersonalData(long userId, LocalDateTime now) {
		jdbc.update("UPDATE subscriptions SET status='CANCELED',ended_at=?,cancel_requested_at=COALESCE(cancel_requested_at,?) WHERE user_id=? AND status IN ('ACTIVE','CANCEL_REQUESTED')", now, now, userId);
		jdbc.update("DELETE FROM follows WHERE follower_id=? OR following_id=?", userId, userId);
		jdbc.update("DELETE FROM likes WHERE user_id=?", userId);
		jdbc.update("DELETE FROM bookmarks WHERE user_id=?", userId);
		jdbc.update("UPDATE chat_members SET left_at=COALESCE(left_at,?) WHERE user_id=?", now, userId);
		jdbc.update("""
			UPDATE project_application_portfolio_snapshots s SET content=jsonb_set(jsonb_set(s.content,'{author,tag}','null'::jsonb),'{author,profileImage}','null'::jsonb)
			FROM project_application_portfolios link,project_applications a
			WHERE s.application_portfolio_id=link.id AND link.application_id=a.id AND a.applicant_member_id=?
			""", userId);
		// 본문·지원·참여·리뷰·거래 기록은 보존한다. 공개 식별 정보는 users를 통해 제거된다.
		jdbc.update("""
			UPDATE users SET email=NULL,password_hash=NULL,kakao_id=NULL,tag=NULL,role=NULL,status=NULL,
			external_links=NULL,trust_score=NULL,payment_methods=NULL,collaboration_status=NULL,
			profile_image=NULL,banner_image=NULL,summary=NULL,career=NULL,contact_hours=NULL,
			job_categories=NULL,skills=NULL,interests=NULL,introduction=NULL,introduction_format=NULL WHERE id=?
			""", userId);
	}
}
