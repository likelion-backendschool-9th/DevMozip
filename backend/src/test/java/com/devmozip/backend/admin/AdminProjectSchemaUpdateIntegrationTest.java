package com.devmozip.backend.admin;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 공고가 이미 있는 projects에 ddl-auto: update가 is_restricted를 추가하는지 확인한다 (docs/admin-plan.md 3.3).
 * 기본값 없는 NOT NULL 칼럼은 PostgreSQL이 거절하므로, 기존 공고가 숨기지 않은 상태로 채워지는지 본다.
 * 표는 개발 DB의 projects 칼럼(2026-11-10 읽기 확인)과 같게 만든다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AdminProjectSchemaUpdateIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@Autowired JdbcTemplate jdbc;

	@BeforeAll
	static void createProjectsWithoutRestrictionColumn() throws SQLException {
		try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
			var statement = connection.createStatement()) {
			statement.execute("""
				CREATE TABLE projects (id BIGSERIAL PRIMARY KEY, host_member_id BIGINT NOT NULL, title VARCHAR(255) NOT NULL,
				summary VARCHAR(255), description TEXT NOT NULL, goal TEXT, recruitment_slots JSONB NOT NULL, tech_stacks JSONB NOT NULL,
				interests JSONB NOT NULL, image_url VARCHAR(2048) NOT NULL, is_public BOOLEAN NOT NULL, status VARCHAR(30) NOT NULL,
				view_count BIGINT NOT NULL DEFAULT 0, recruitment_start DATE NOT NULL, recruitment_end DATE NOT NULL,
				development_start DATE NOT NULL, development_end DATE NOT NULL, completed_at TIMESTAMP, deleted_at TIMESTAMP,
				created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)""");
			statement.execute("""
				INSERT INTO projects(host_member_id, title, description, recruitment_slots, tech_stacks, interests, image_url, is_public,
				status, recruitment_start, recruitment_end, development_start, development_end, created_at, updated_at)
				VALUES (1, '기존 공고', '설명', '[]', '[]', '[]', 'https://example.test/a.png', true, 'RECRUITING',
				DATE '2026-11-01', DATE '2026-11-30', DATE '2026-11-01', DATE '2027-01-31', localtimestamp, localtimestamp)""");
		}
	}

	@Test
	void addsNotNullColumnAndExistingProjectsAreNotRestricted() {
		Map<String, Object> column = jdbc.queryForMap("SELECT data_type, is_nullable, column_default FROM information_schema.columns"
			+ " WHERE table_schema = 'public' AND table_name = 'projects' AND column_name = 'is_restricted'");
		assertEquals("boolean", column.get("data_type"));
		assertEquals("NO", column.get("is_nullable"));
		assertEquals("false", column.get("column_default"));
		assertEquals(List.of(false), jdbc.queryForList("SELECT is_restricted FROM projects ORDER BY id", Boolean.class));
	}
}
