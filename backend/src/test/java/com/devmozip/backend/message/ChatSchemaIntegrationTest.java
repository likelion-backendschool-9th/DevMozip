package com.devmozip.backend.message;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.devmozip.backend.entity.ChatRoom;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ChatSchemaIntegrationTest extends ChatEntityRoundTripTests {
	@Container @ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@Autowired JdbcTemplate jdbc;
	@Autowired ProjectRepository projects;

	@Test void columnsMatchErd() {
		assertEquals(Map.of(
			"id", "bigint",
			"type", "character varying(20)",
			"project_id", "bigint",
			"deleted_at", "timestamp without time zone"), columns("chat_rooms"));
		assertEquals(Map.of(
			"id", "bigint",
			"room_id", "bigint",
			"user_id", "bigint",
			"last_read_at", "timestamp without time zone",
			"joined_at", "timestamp without time zone",
			"left_at", "timestamp without time zone"), columns("chat_members"));
		assertEquals(Map.of(
			"id", "bigint",
			"chat_member_id", "bigint",
			"content", "text",
			"sent_at", "timestamp without time zone",
			"deleted_at", "timestamp without time zone"), columns("messages"));
	}

	@Test void createsConstraintsAndIndexes() {
		assertConstraintsAndIndexes(jdbc);
	}

	@Test void rejectsSecondRowForSameMemberInRoom() {
		long userId = insertUser("duplicate@devmozip.test", "중복회원");
		long roomId = insertRoom();
		insertChatMember(roomId, userId);
		assertThrows(DataIntegrityViolationException.class, () -> insertChatMember(roomId, userId));
	}

	@Test void rejectsMessageFromUnknownParticipant() {
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
			"INSERT INTO messages (chat_member_id, content, sent_at) VALUES (?, '본문', now())", Long.MAX_VALUE));
	}

	@Test void rejectsUnknownRoomType() {
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO chat_rooms (type) VALUES ('GROUP')"));
	}

	// H2에는 projects 테이블이 만들어지지 않아 PostgreSQL에서만 확인한다.
	@Test @Transactional void savesProjectRoom() {
		Project project = saveProject();
		ChatRoom room = rooms.save(ChatRoom.project(project));
		entityManager.flush();
		entityManager.clear();

		ChatRoom found = rooms.findById(room.getId()).orElseThrow();
		assertEquals(ChatRoom.Type.PROJECT, found.getType());
		assertEquals(project.getId(), found.getProject().getId());
		assertNull(found.getDeletedAt());
	}

	@Test void rejectsSecondRoomForSameProject() {
		long projectId = saveProject().getId();
		insertProjectRoom(projectId);
		assertThrows(DataIntegrityViolationException.class, () -> insertProjectRoom(projectId));
	}

	@Test void rejectsRoomForUnknownProject() {
		assertThrows(DataIntegrityViolationException.class, () -> insertProjectRoom(Long.MAX_VALUE));
	}

	static void assertConstraintsAndIndexes(JdbcTemplate jdbc) {
		List<String> roomIndexes = jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE tablename = 'chat_rooms'", String.class);
		assertTrue(roomIndexes.stream().anyMatch(index -> index.contains("UNIQUE") && index.contains("(project_id)")), roomIndexes.toString());
		List<String> roomKeys = foreignKeys(jdbc, "chat_rooms");
		assertTrue(roomKeys.contains("FOREIGN KEY (project_id) REFERENCES projects(id)"), roomKeys.toString());
		List<String> memberIndexes = jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE tablename = 'chat_members'", String.class);
		assertTrue(memberIndexes.stream().anyMatch(index -> index.contains("UNIQUE") && index.contains("(room_id, user_id)")), memberIndexes.toString());
		assertTrue(memberIndexes.stream().anyMatch(index -> index.contains("idx_chat_members_user_id") && index.contains("(user_id)")), memberIndexes.toString());
		List<String> messageIndexes = jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE tablename = 'messages'", String.class);
		assertTrue(messageIndexes.stream().anyMatch(index -> index.contains("idx_messages_chat_member_id_id") && index.contains("(chat_member_id, id)")), messageIndexes.toString());
		List<String> memberKeys = foreignKeys(jdbc, "chat_members");
		assertTrue(memberKeys.contains("FOREIGN KEY (room_id) REFERENCES chat_rooms(id)"), memberKeys.toString());
		assertTrue(memberKeys.contains("FOREIGN KEY (user_id) REFERENCES users(id)"), memberKeys.toString());
		List<String> messageKeys = foreignKeys(jdbc, "messages");
		assertTrue(messageKeys.contains("FOREIGN KEY (chat_member_id) REFERENCES chat_members(id)"), messageKeys.toString());
	}

	private static List<String> foreignKeys(JdbcTemplate jdbc, String table) {
		return jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = ?::regclass AND contype = 'f'", String.class, table);
	}

	private Map<String, String> columns(String table) {
		Map<String, String> columns = new TreeMap<>();
		jdbc.query("SELECT column_name, data_type, character_maximum_length FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?",
			row -> {
				Object length = row.getObject("character_maximum_length");
				columns.put(row.getString("column_name"), row.getString("data_type") + (length == null ? "" : "(" + length + ")"));
			}, table);
		return columns;
	}

	private long insertUser(String email, String nickname) {
		return jdbc.queryForObject("INSERT INTO users (email, nickname) VALUES (?, ?) RETURNING id", Long.class, email, nickname);
	}

	private long insertRoom() {
		return jdbc.queryForObject("INSERT INTO chat_rooms (type) VALUES ('DIRECT') RETURNING id", Long.class);
	}

	private void insertChatMember(long roomId, long userId) {
		jdbc.update("INSERT INTO chat_members (room_id, user_id, joined_at) VALUES (?, ?, now())", roomId, userId);
	}

	private void insertProjectRoom(long projectId) {
		jdbc.update("INSERT INTO chat_rooms (type, project_id) VALUES ('PROJECT', ?)", projectId);
	}

	private Project saveProject() {
		LocalDate today = LocalDate.of(2026, 11, 6);
		return projects.saveAndFlush(new Project(1L, "프로젝트", "요약", "소개", null, "https://example.com/a.png", true,
			List.of(), List.of(), List.of(), today, today.plusDays(7), today.plusDays(8), today.plusMonths(2), today.atStartOfDay()));
	}
}
