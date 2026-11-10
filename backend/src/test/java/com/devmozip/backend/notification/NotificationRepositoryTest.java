package com.devmozip.backend.notification;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.devmozip.backend.repository.NotificationQueryRepository;
import com.devmozip.backend.repository.NotificationQueryRepository.Filter;
import com.devmozip.backend.repository.NotificationRepository;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** notifications 매핑과 목록 SQL을 이 테스트 전용 PostgreSQL 컨테이너에서 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationRepositoryTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@Autowired NotificationRepository notifications;
	@Autowired NotificationQueryRepository queries;
	@Autowired JdbcTemplate jdbc;
	@Autowired PlatformTransactionManager transactionManager;

	static final LocalDateTime NOW = LocalDateTime.parse("2026-11-10T12:00:00");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.sql.init.mode", () -> "never");
	}

	@BeforeEach
	void fixtures() {
		jdbc.update("DELETE FROM notifications");
		jdbc.update("DELETE FROM users");
		for (long id : new long[] {1, 2}) {
			jdbc.update("INSERT INTO users(id, email, role, status) VALUES (?, ?, 'USER', 'ACTIVE')", id, "notice" + id + "@test.local");
		}
	}

	long insert(long recipient, String token, String createdAt, String readAt) {
		return jdbc.queryForObject("""
			INSERT INTO notifications(recipient_id, token, type, title, body, action_url, read_at, created_at)
			VALUES (?, ?, 'PROJECT_STATUS_CHANGED', '프로젝트 상태 변경', '본문', '/projects/1', ?::timestamp, ?::timestamp) RETURNING id
			""", Long.class, recipient, token, readAt, createdAt);
	}

	@Test
	void generatedTableFollowsErdColumnsConstraintsAndIndex() {
		var columns = jdbc.queryForList("""
			SELECT column_name, data_type, character_maximum_length, is_nullable FROM information_schema.columns
			WHERE table_name = 'notifications' ORDER BY column_name
			""");
		assertEquals(11, columns.size());
		Map<String, String> expected = Map.ofEntries(
			Map.entry("id", "bigint/null/NO"), Map.entry("recipient_id", "bigint/null/NO"),
			Map.entry("project_id", "bigint/null/YES"), Map.entry("review_id", "bigint/null/YES"),
			Map.entry("token", "character varying/200/NO"), Map.entry("type", "character varying/50/NO"),
			Map.entry("title", "character varying/255/NO"), Map.entry("body", "text/null/NO"),
			Map.entry("action_url", "character varying/2048/NO"),
			Map.entry("read_at", "timestamp without time zone/null/YES"),
			Map.entry("created_at", "timestamp without time zone/null/NO"));
		for (var column : columns) {
			assertEquals(expected.get((String) column.get("column_name")),
				column.get("data_type") + "/" + column.get("character_maximum_length") + "/" + column.get("is_nullable"),
				(String) column.get("column_name"));
		}
		List<String> references = jdbc.queryForList("""
			SELECT kcu.column_name || '->' || ccu.table_name || '.' || ccu.column_name
			FROM information_schema.table_constraints tc
			JOIN information_schema.key_column_usage kcu ON kcu.constraint_name = tc.constraint_name
			JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name
			WHERE tc.table_name = 'notifications' AND tc.constraint_type = 'FOREIGN KEY' ORDER BY 1
			""", String.class);
		assertEquals(List.of("project_id->projects.id", "recipient_id->users.id", "review_id->reviews.id"), references);
		assertEquals(List.of("CASCADE", "CASCADE", "CASCADE"), jdbc.queryForList("""
			SELECT r.delete_rule FROM information_schema.referential_constraints r
			JOIN information_schema.table_constraints t ON t.constraint_name = r.constraint_name
			WHERE t.table_name = 'notifications'
			""", String.class));
		assertEquals("recipient_id,token", jdbc.queryForObject("""
			SELECT string_agg(a.attname, ',' ORDER BY array_position(c.conkey, a.attnum)) FROM pg_constraint c
			JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
			WHERE c.conrelid = 'notifications'::regclass AND c.contype = 'u'
			""", String.class));
		assertEquals("recipient_id,created_at,id", jdbc.queryForObject("""
			SELECT string_agg(a.attname, ',' ORDER BY array_position(i.indkey, a.attnum)) FROM pg_index i
			JOIN pg_class ic ON ic.oid = i.indexrelid
			JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
			WHERE ic.relname = 'idx_notifications_recipient_created'
			""", String.class));
	}

	@Test
	void sameRecipientAndTokenIsRejectedButOtherRecipientIsAllowed() {
		insert(1, "project-application:1:SUBMITTED", "2026-11-10 10:00:00", null);
		insert(2, "project-application:1:SUBMITTED", "2026-11-10 10:00:00", null);
		assertThrows(DataIntegrityViolationException.class,
			() -> insert(1, "project-application:1:SUBMITTED", "2026-11-10 10:01:00", null));
		assertTrue(notifications.existsByRecipientIdAndToken(1, "project-application:1:SUBMITTED"));
		assertFalse(notifications.existsByRecipientIdAndToken(1, "project-application:2:SUBMITTED"));
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
			INSERT INTO notifications(recipient_id, project_id, token, type, title, body, action_url, created_at)
			VALUES (1, 999, 'missing-project', 'PROJECT_COMPLETED', '제목', '본문', '/projects/999', localtimestamp)
			"""));
	}

	@Test
	void physicallyDeletingRecipientDeletesOnlyTheirNotifications() {
		insert(1, "a", "2026-11-10 09:00:00", null);
		insert(2, "b", "2026-11-10 09:00:00", null);
		jdbc.update("DELETE FROM users WHERE id = 1");
		assertEquals(List.of(2L), jdbc.queryForList("SELECT recipient_id FROM notifications", Long.class));
	}

	@Test
	void listIsNewestFirstWithIdTieBreakAndPages() {
		long oldest = insert(1, "a", "2026-11-10 09:00:00", null);
		long tieLow = insert(1, "b", "2026-11-10 10:00:00", null);
		long tieHigh = insert(1, "c", "2026-11-10 10:00:00", null);
		long newest = insert(1, "d", "2026-11-10 11:00:00", null);
		insert(2, "e", "2026-11-10 12:00:00", null);

		var first = queries.find(1, Filter.ALL, 0, 3, NOW);
		assertEquals(List.of(newest, tieHigh, tieLow), first.rows().stream().map(NotificationQueryRepository.Row::id).toList());
		assertTrue(first.hasNext());
		var second = queries.find(1, Filter.ALL, 1, 3, NOW);
		assertEquals(List.of(oldest), second.rows().stream().map(NotificationQueryRepository.Row::id).toList());
		assertFalse(second.hasNext());
		assertTrue(queries.find(1, Filter.ALL, 2, 3, NOW).rows().isEmpty());

		var row = first.rows().getFirst();
		assertEquals("PROJECT_STATUS_CHANGED", row.type());
		assertEquals("/projects/1", row.actionUrl());
		assertNull(row.projectId());
		assertEquals(LocalDateTime.parse("2026-11-10T11:00:00"), row.createdAt());
	}

	@Test
	void filtersUnreadCountAndRecipientsAreSeparated() {
		long unread = insert(1, "a", "2026-11-10 09:00:00", null);
		long read = insert(1, "b", "2026-11-10 10:00:00", "2026-11-10 10:30:00");
		insert(2, "c", "2026-11-10 11:00:00", null);
		insert(2, "d", "2026-11-10 11:30:00", null);

		assertEquals(List.of(unread), queries.find(1, Filter.UNREAD, 0, 20, NOW).rows().stream().map(NotificationQueryRepository.Row::id).toList());
		assertEquals(List.of(read), queries.find(1, Filter.READ, 0, 20, NOW).rows().stream().map(NotificationQueryRepository.Row::id).toList());
		assertEquals(2, queries.find(1, Filter.ALL, 0, 20, NOW).rows().size());
		assertEquals(1, queries.countUnread(1, NOW));
		assertEquals(2, queries.countUnread(2, NOW));

		assertTrue(notifications.findByIdAndRecipientId(unread, 1).isPresent());
		assertTrue(notifications.findByIdAndRecipientId(unread, 2).isEmpty());
	}

	@Test
	void markAllReadOnlyChangesUnreadRowsOfRecipient() {
		insert(1, "a", "2026-11-10 09:00:00", null);
		insert(1, "b", "2026-11-10 10:00:00", "2026-11-10 10:30:00");
		insert(2, "c", "2026-11-10 11:00:00", null);
		var tx = new TransactionTemplate(transactionManager);
		Integer changed = tx.execute(status -> notifications.markAllRead(1, LocalDateTime.parse("2026-11-10T12:00:00")));
		assertEquals(1, changed);
		assertEquals(0, queries.countUnread(1, NOW));
		assertEquals(1, queries.countUnread(2, NOW));
		// a는 안 읽음이었으므로 지금 시각, b는 이미 읽었으므로 처음 읽은 시각 그대로다.
		assertEquals(List.of("2026-11-10 12:00:00", "2026-11-10 10:30:00"), jdbc.queryForList(
			"SELECT to_char(read_at, 'YYYY-MM-DD HH24:MI:SS') FROM notifications WHERE recipient_id = 1 ORDER BY id", String.class));
	}
}
