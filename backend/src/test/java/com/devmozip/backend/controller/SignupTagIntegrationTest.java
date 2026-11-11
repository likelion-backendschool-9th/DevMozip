package com.devmozip.backend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devmozip.backend.security.jwt.RefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 회원가입이 이메일 @ 앞부분을 users.tag에 저장하는지 확인한다. 회원은 이 테스트 전용 PostgreSQL 컨테이너에만 만든다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SignupTagIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.sql.init.mode", () -> "never");
	}

	@BeforeEach
	void clean() {
		jdbc.update("DELETE FROM users");
	}

	void signup(String email, String nickname) throws Exception {
		mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
			{"email":"%s","password":"Devmozip123","confirmPassword":"Devmozip123","nickname":"%s",
			 "job":"백엔드 개발자","career":"2년 차","skills":["Java"],"interests":["교육"],"available":true}
			""".formatted(email, nickname))).andExpect(status().isCreated());
	}

	String storedTag(String email) {
		return jdbc.queryForObject("SELECT tag FROM users WHERE email = ?", String.class, email);
	}

	@Test
	void signupStoresEmailLocalPartAsTag() throws Exception {
		// 이메일은 소문자로 정리해 저장하므로 태그도 소문자다.
		signup("Dev_Kim-01@Test.Local", "첫회원");

		assertEquals("dev_kim-01", storedTag("dev_kim-01@test.local"));
	}

	@Test
	void signupLeavesTagEmptyWhenLocalPartIsTakenOrInvalid() throws Exception {
		// 프로필 수정처럼 대소문자를 무시하고 중복을 본다. 가입은 막지 않고 태그만 비워 둔다.
		jdbc.update("INSERT INTO users(email, nickname, tag, role, status) VALUES ('old@test.local', '기존회원', 'Kim', 'USER', 'ACTIVE')");
		signup("kim@other.local", "같은앞부분");
		signup("john.doe@test.local", "점포함");

		assertNull(storedTag("kim@other.local"));
		assertNull(storedTag("john.doe@test.local"));
		assertEquals("Kim", storedTag("old@test.local"));
	}
}
