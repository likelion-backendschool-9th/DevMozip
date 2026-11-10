package com.devmozip.backend.message;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** ddl-auto: update가 이전 구조로 이미 있는 테이블에도 컬럼, 제약, 인덱스를 추가하는지 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ChatSchemaUpdateIntegrationTest {
	@Container @ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@Autowired JdbcTemplate jdbc;

	// 스프링 컨텍스트가 뜨기 전에 실행되어, Hibernate가 테이블이 이미 있는 상태에서 시작한다.
	@BeforeAll static void createTablesWithoutConstraints() throws SQLException {
		try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
			var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE chat_rooms (id BIGSERIAL PRIMARY KEY, type VARCHAR(20) NOT NULL)");
			statement.execute("CREATE TABLE chat_members (id BIGSERIAL PRIMARY KEY, room_id BIGINT NOT NULL, user_id BIGINT NOT NULL, last_read_at TIMESTAMP, joined_at TIMESTAMP NOT NULL, left_at TIMESTAMP)");
			statement.execute("CREATE TABLE messages (id BIGSERIAL PRIMARY KEY, chat_member_id BIGINT NOT NULL, content TEXT NOT NULL, sent_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP)");
		}
	}

	@Test void addsColumnsToExistingRoomTable() {
		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'chat_rooms' AND column_name IN ('project_id', 'deleted_at')", Integer.class));
	}

	@Test void addsConstraintsAndIndexesToExistingTables() {
		ChatSchemaIntegrationTest.assertConstraintsAndIndexes(jdbc);
	}
}
