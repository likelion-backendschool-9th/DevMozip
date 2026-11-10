package com.devmozip.backend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 회원이 이미 있는 users 테이블에 ddl-auto: update가 is_private를 추가하는지 확인한다(docs/privacy-settings-plan.md 3.3).
 * 기본값 없이 NOT NULL 칼럼을 추가하면 PostgreSQL이 거절하므로, 기존 회원이 공개 계정으로 채워지는지를 함께 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AccountLockSchemaUpdateIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@Autowired JdbcTemplate jdbc;
	@Autowired MockMvc mvc;
	@Autowired JwtProvider jwt;

	// 스프링 컨텍스트가 뜨기 전에 실행되어, Hibernate가 is_private 없는 users와 기존 회원이 있는 상태에서 시작한다.
	@BeforeAll
	static void createUsersWithoutPrivacyColumn() throws SQLException {
		try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
			var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE users (id BIGSERIAL PRIMARY KEY, email VARCHAR(255), nickname VARCHAR(50), role VARCHAR(20), status VARCHAR(20))");
			statement.execute("INSERT INTO users(email, nickname, role, status) VALUES"
				+ " ('old1@test.local', '기존회원1', 'USER', 'ACTIVE'), ('old2@test.local', '기존회원2', 'PREMIUM', 'WARNED')");
		}
	}

	@Test
	void addsNotNullColumnAndExistingMembersArePublic() throws Exception {
		Map<String, Object> column = jdbc.queryForMap("SELECT data_type, is_nullable, column_default FROM information_schema.columns"
			+ " WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'is_private'");
		assertEquals("boolean", column.get("data_type"));
		assertEquals("NO", column.get("is_nullable"));
		assertEquals("false", column.get("column_default"));
		assertEquals(List.of(false, false), jdbc.queryForList("SELECT is_private FROM users ORDER BY id", Boolean.class));

		mvc.perform(get("/api/v1/users/me/account-lock").header("Authorization", "Bearer " + jwt.createAccessToken("old1@test.local", "USER")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.isPrivate").value(false));
	}
}
