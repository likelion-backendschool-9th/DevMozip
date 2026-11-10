package com.devmozip.backend.follow;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import com.devmozip.backend.entity.Follow;
import com.devmozip.backend.entity.FollowId;
import com.devmozip.backend.repository.FollowQueryRepository;
import com.devmozip.backend.repository.FollowQueryRepository.Direction;
import com.devmozip.backend.repository.FollowRepository;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** follows 매핑과 목록 SQL을 이 테스트 전용 PostgreSQL 컨테이너에서 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FollowRepositoryTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@Autowired FollowRepository follows;
	@Autowired FollowQueryRepository queries;
	@Autowired MemberRepository members;
	@Autowired JdbcTemplate jdbc;
	@Autowired PlatformTransactionManager transactionManager;
	TransactionTemplate tx;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.sql.init.mode", () -> "never");
	}

	@BeforeEach
	void fixtures() {
		tx = new TransactionTemplate(transactionManager);
		jdbc.update("DELETE FROM follows");
		jdbc.update("DELETE FROM users");
		user(1, "기준회원", "base-user", "ACTIVE", "[\"백엔드 개발자\"]", "[\"Java\"]", "AVAILABLE");
		user(2, "Alpha 개발자", "alpha", "ACTIVE", "[\"프론트엔드 개발자\"]", "[\"React\", \"TypeScript\"]", "AVAILABLE");
		user(3, "베타", "beta_100%", "WARNED", "[\"데이터 엔지니어\"]", "[\"Python\"]", "BUSY");
		user(4, "태그없음", null, "ACTIVE", "[]", "[]", null);
		user(5, "정지회원", "suspended-user", "SUSPENDED", "[\"백엔드 개발자\"]", "[\"Kotlin\"]", "AVAILABLE");
		user(6, "잘못된값", "broken-json", "ACTIVE", "\"백엔드\"", "{\"skill\": \"Java\"}", "AVAILABLE");
	}

	void user(long id, String nickname, String tag, String status, String jobs, String skills, String collaboration) {
		jdbc.update("INSERT INTO users(id, email, nickname, tag, role, status, job_categories, skills, collaboration_status)"
			+ " VALUES (?, ?, ?, ?, 'USER', ?, ?::jsonb, ?::jsonb, ?)",
			id, "follow" + id + "@test.local", nickname, tag, status, jobs, skills, collaboration);
	}

	void follow(long follower, long following, String createdAt, boolean notification) {
		jdbc.update("INSERT INTO follows(follower_id, following_id, activity_notification_enabled, created_at) VALUES (?, ?, ?, ?::timestamp)",
			follower, following, notification, createdAt);
	}

	@Test
	void generatedTableUsesCompositeKeyForeignKeysAndFollowingIndex() {
		List<String> primaryKey = jdbc.queryForList("""
			SELECT a.attname FROM pg_index i JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
			WHERE i.indrelid = 'follows'::regclass AND i.indisprimary ORDER BY array_position(i.indkey, a.attnum)
			""", String.class);
		assertEquals(List.of("follower_id", "following_id"), primaryKey);
		assertEquals(2, jdbc.queryForObject("""
			SELECT count(*) FROM information_schema.referential_constraints r
			JOIN information_schema.table_constraints t ON t.constraint_name = r.constraint_name
			JOIN information_schema.constraint_column_usage c ON c.constraint_name = r.unique_constraint_name
			WHERE t.table_name = 'follows' AND c.table_name = 'users' AND c.column_name = 'id'
			""", Integer.class));
		assertEquals(4, jdbc.queryForObject(
			"SELECT count(*) FROM information_schema.columns WHERE table_name = 'follows' AND is_nullable = 'NO'", Integer.class));
		assertEquals("false", jdbc.queryForObject(
			"SELECT column_default FROM information_schema.columns WHERE table_name = 'follows' AND column_name = 'activity_notification_enabled'", String.class));
		assertTrue(jdbc.queryForObject(
			"SELECT indexdef FROM pg_indexes WHERE tablename = 'follows' AND indexname = 'idx_follows_following_id'", String.class)
			.contains("(following_id)"));
	}

	@Test
	void savesNewRelationWithNotificationOffAndRejectsSecondInsertOfSamePair() {
		LocalDateTime now = LocalDateTime.of(2026, 11, 9, 12, 0);
		tx.executeWithoutResult(status -> follows.saveAndFlush(Follow.create(members.getReferenceById(1L), members.getReferenceById(2L), now)));

		Follow saved = tx.execute(status -> follows.findById(new FollowId(1L, 2L)).orElseThrow());
		assertFalse(saved.isActivityNotificationEnabled());
		assertEquals(now, saved.getCreatedAt());
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM follows", Integer.class));

		// 같은 관계를 다시 저장하면 조회 후 덮어쓰지 않고 기본 키 충돌로 실패한다.
		assertThrows(DataIntegrityViolationException.class, () -> tx.executeWithoutResult(status ->
			follows.saveAndFlush(Follow.create(members.getReferenceById(1L), members.getReferenceById(2L), now.plusDays(1)))));
		assertEquals(now, jdbc.queryForObject("SELECT created_at FROM follows", LocalDateTime.class));

		assertThrows(DataIntegrityViolationException.class, () -> follow(1, 999, "2026-11-09 12:00:00", false));
	}

	@Test
	void countsIncludeEveryRelationRegardlessOfStatusOrTag() {
		follow(2, 1, "2026-11-01 10:00:00", false);
		follow(3, 1, "2026-11-02 10:00:00", false);
		follow(4, 1, "2026-11-03 10:00:00", false);
		follow(5, 1, "2026-11-04 10:00:00", false);
		follow(1, 2, "2026-11-05 10:00:00", false);
		assertEquals(4, follows.countFollowers(1));
		assertEquals(1, follows.countFollowing(1));
		assertEquals(1, follows.countFollowers(2));
		assertEquals(0, follows.countFollowing(6));
	}

	@Test
	void deleteAndNotificationUpdateReportChangedRows() {
		follow(1, 2, "2026-11-01 10:00:00", false);
		assertEquals(1, (int) tx.execute(status -> follows.updateActivityNotification(1, 2, true)));
		assertTrue(jdbc.queryForObject("SELECT activity_notification_enabled FROM follows", Boolean.class));
		assertEquals(0, (int) tx.execute(status -> follows.updateActivityNotification(2, 1, true)));
		assertEquals(1, (int) tx.execute(status -> follows.deleteRelation(1, 2)));
		assertEquals(0, (int) tx.execute(status -> follows.deleteRelation(1, 2)));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM follows", Integer.class));
	}

	@Test
	void listsNewestFirstWithRequesterStateAndPages() {
		follow(2, 1, "2026-11-01 10:00:00", false);
		follow(3, 1, "2026-11-03 10:00:00", false);
		follow(4, 1, "2026-11-02 10:00:00", false);
		follow(5, 1, "2026-11-02 10:00:00", false);
		follow(6, 2, "2026-11-04 10:00:00", false);
		follow(6, 3, "2026-11-04 10:00:00", true);

		var first = queries.find(1, Direction.FOLLOWERS, null, null, false, 0, 3);
		assertEquals(4, first.total());
		assertEquals(List.of(3L, 5L, 4L), first.rows().stream().map(FollowQueryRepository.Row::userId).toList());
		assertTrue(first.rows().stream().noneMatch(FollowQueryRepository.Row::followed));
		assertEquals(List.of(2L), queries.find(1, Direction.FOLLOWERS, null, null, false, 1, 3).rows().stream()
			.map(FollowQueryRepository.Row::userId).toList());
		assertTrue(queries.find(1, Direction.FOLLOWERS, null, null, false, 5, 3).rows().isEmpty());

		// 요청자(6)가 각 사람을 팔로우하는지와 알림 설정을 함께 읽는다.
		var withRequester = queries.find(1, Direction.FOLLOWERS, 6L, null, false, 0, 10).rows();
		assertEquals(new FollowQueryRepository.Row(3, true, true), withRequester.get(0));
		assertEquals(new FollowQueryRepository.Row(2, true, false), withRequester.get(3));
		assertFalse(withRequester.get(1).followed());

		assertEquals(List.of(3L, 2L), queries.find(6, Direction.FOLLOWING, null, null, false, 0, 10).rows().stream()
			.map(FollowQueryRepository.Row::userId).toList());
	}

	@Test
	void searchesNicknameTagJobAndSkillCaseInsensitivelyAndFiltersAvailable() {
		for (long id = 2; id <= 6; id++) follow(1, id, "2026-11-0" + id + " 10:00:00", false);
		assertEquals(List.of(2L), ids("ALPHA"));
		assertEquals(List.of(2L), ids("typescript"));
		assertEquals(List.of(3L), ids("데이터"));
		assertEquals(List.of(5L), ids("suspended"));
		assertEquals(List.of(4L), ids("태그"));
		// LIKE 특수 문자는 글자 그대로 찾는다. 와일드카드로 쓰이면 alpha·전체가 함께 나온다.
		assertEquals(List.of(3L), ids("%"));
		assertEquals(List.of(3L), ids("a_"));
		assertEquals(List.of(), ids("!"));
		// 배열이 아닌 JSON 값(6번 회원)은 검색에서 빠지고 오류를 내지 않는다.
		assertEquals(List.of(5L), ids("백엔드"));
		assertEquals(List.of(), ids("java"));
		assertEquals(List.of(6L, 5L, 4L, 3L, 2L), ids(null));
		assertEquals(3, queries.find(1, Direction.FOLLOWING, null, null, true, 0, 10).total());
		assertEquals(List.of(2L), queries.find(1, Direction.FOLLOWING, null, "react", true, 0, 10).rows().stream()
			.map(FollowQueryRepository.Row::userId).toList());
	}

	List<Long> ids(String query) {
		return queries.find(1, Direction.FOLLOWING, null, query, false, 0, 10).rows().stream()
			.map(FollowQueryRepository.Row::userId).toList();
	}
}
