package com.devmozip.backend.message;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 프로젝트 채팅 테스트의 프로젝트와 팀원. PostgreSQL 전용이다(projects의 jsonb).
 * 엔티티 생성자와 승인 코드(#48)는 다른 작업에서 바뀌므로 SQL로 넣는다. 승인 팀원은 #48의 결과와 같은 행이다.
 */
public final class ProjectChatTestData {
	private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 1, 9, 0);
	private final JdbcTemplate jdbc;

	public ProjectChatTestData(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** 주최자를 ACTIVE 멤버로 둔 프로젝트 */
	public long project(long hostId, String title, LocalDate recruitmentStart) {
		Long id = jdbc.queryForObject("""
			INSERT INTO projects (host_member_id, title, description, recruitment_slots, tech_stacks, interests, image_url,
				is_public, status, view_count, recruitment_start, recruitment_end, development_start, development_end,
				created_at, updated_at)
			VALUES (?, ?, '설명', '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, 'https://example.com/project.webp',
				true, 'RECRUITING', 0, ?, ?, ?, ?, ?, ?)
			RETURNING id""", Long.class, hostId, title, recruitmentStart, recruitmentStart.plusDays(7),
			recruitmentStart.plusDays(8), recruitmentStart.plusDays(60), CREATED, CREATED);
		insertMember(id, hostId, "HOST");
		return id;
	}

	/** #48의 승인과 같은 결과(PARTICIPANT, ACTIVE) */
	public void approve(long projectId, long memberId) {
		insertMember(projectId, memberId, "PARTICIPANT");
	}

	/** #48의 참여 취소와 같은 결과(LEFT) */
	public void leave(long projectId, long memberId) {
		jdbc.update("UPDATE project_members SET status = 'LEFT', left_at = ? WHERE project_id = ? AND user_id = ?",
			CREATED.plusDays(1), projectId, memberId);
	}

	public void status(long projectId, String status) {
		jdbc.update("UPDATE projects SET status = ? WHERE id = ?", status, projectId);
	}

	public void deleteProject(long projectId) {
		jdbc.update("UPDATE projects SET deleted_at = ? WHERE id = ?", CREATED.plusDays(2), projectId);
	}

	public int rooms(long projectId) {
		return jdbc.queryForObject("SELECT count(*) FROM chat_rooms WHERE project_id = ?", Integer.class, projectId);
	}

	public int chatMemberRows(long projectId) {
		return jdbc.queryForObject("""
			SELECT count(*) FROM chat_members c JOIN chat_rooms r ON r.id = c.room_id WHERE r.project_id = ?""",
			Integer.class, projectId);
	}

	public int messageRows(long projectId) {
		return jdbc.queryForObject("""
			SELECT count(*) FROM messages m JOIN chat_members c ON c.id = m.chat_member_id
			JOIN chat_rooms r ON r.id = c.room_id WHERE r.project_id = ?""", Integer.class, projectId);
	}

	private void insertMember(long projectId, long memberId, String role) {
		jdbc.update("""
			INSERT INTO project_members (project_id, user_id, role, status, joined_at, created_at, updated_at)
			VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?)""", projectId, memberId, role, CREATED, CREATED, CREATED);
	}
}
