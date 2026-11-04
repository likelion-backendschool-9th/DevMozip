package com.devmozip.backend.ai.controller;

import com.devmozip.backend.security.jwt.RefreshTokenStore;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiAuthenticatedConversationTests {
	private static final String BASE = "/api/v1/ai/conversations";
	private static final String PASSWORD = "Ai-auth-test-password!";
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired ObjectMapper mapper;
	@MockitoBean RefreshTokenStore refreshTokens;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> UUID.randomUUID().toString() + UUID.randomUUID());
		registry.add("spring.datasource.url", () -> "jdbc:h2:mem:ai-authenticated;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
		registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
		registry.add("spring.datasource.username", () -> "sa");
		registry.add("spring.datasource.password", () -> "");
		registry.add("spring.sql.init.mode", () -> "always");
		registry.add("spring.sql.init.schema-locations", () -> "classpath:ai/schema.sql");
		registry.add("management.health.redis.enabled", () -> "false");
		registry.add("ai.current-user.development-enabled", () -> "false");
	}

	@BeforeEach
	void reset() {
		jdbc.update("DELETE FROM ai_messages");
		jdbc.update("DELETE FROM ai_conversations");
		jdbc.update("DELETE FROM users");
		for (int id : new int[]{101, 202}) {
			jdbc.update("INSERT INTO users(id,email,password_hash,role,status) VALUES (?,?,?,?,?)",
				id, id + "@ai.test", encoder.encode(PASSWORD), "USER", "ACTIVE");
		}
	}

	private String login(int userId) throws Exception {
		String response = mvc.perform(post("/api/auth/login").contentType("application/json")
			.content(mapper.writeValueAsString(Map.of("username", userId + "@ai.test", "password", PASSWORD))))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return "Bearer " + mapper.readTree(response).get("accessToken").asText();
	}

	private long create(String token) throws Exception {
		String response = mvc.perform(post(BASE).header("Authorization", token).contentType("application/json")
			.content("{\"title\":\"my conversation\",\"userId\":202}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return mapper.readTree(response).get("id").asLong();
	}

	@Test
	void realLoginMapsEachEmailToItsOwnUserIdAndProtectsEveryConversationOperation() throws Exception {
		String a = login(101), b = login(202);
		long aId = create(a), bId = create(b);
		assertEquals(101L, jdbc.queryForObject("SELECT user_id FROM ai_conversations WHERE id=?", Long.class, aId));
		assertEquals(202L, jdbc.queryForObject("SELECT user_id FROM ai_conversations WHERE id=?", Long.class, bId));
		mvc.perform(get(BASE).header("Authorization", a)).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(aId));
		mvc.perform(get(BASE).header("Authorization", b)).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(bId));
		for (long id : new long[]{aId, 999999}) {
			mvc.perform(get(BASE + "/" + id).header("Authorization", b)).andExpect(status().isNotFound());
			mvc.perform(post(BASE + "/" + id + "/messages").header("Authorization", b)
				.contentType("application/json").content("{\"content\":\"denied\"}"))
				.andExpect(status().isNotFound());
			mvc.perform(delete(BASE + "/" + id).header("Authorization", b)).andExpect(status().isNotFound());
		}
		mvc.perform(post(BASE + "/" + aId + "/messages").header("Authorization", a)
			.contentType("application/json").content("{\"content\":\"my message\"}"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"));
		mvc.perform(get(BASE + "/" + aId).header("Authorization", a)).andExpect(status().isOk())
			.andExpect(jsonPath("$.messages[0].content").value("my message"));
		mvc.perform(delete(BASE + "/" + aId).header("Authorization", a)).andExpect(status().isNoContent());
		mvc.perform(get(BASE + "/" + bId).header("Authorization", b)).andExpect(status().isOk());
	}

	@Test
	void anonymousAndDeletedUsersCannotUseAiApis() throws Exception {
		mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
		mvc.perform(post(BASE)).andExpect(status().isUnauthorized());
		String token = login(101);
		jdbc.update("DELETE FROM users WHERE id=101");
		mvc.perform(get(BASE).header("Authorization", token)).andExpect(status().isUnauthorized());
		mvc.perform(post(BASE).header("Authorization", token)).andExpect(status().isUnauthorized());
	}

	@Test
	void suspendedUserIsRejectedEvenWithPreviouslyIssuedToken() throws Exception {
		String token = login(101);
		jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=101");
		mvc.perform(get(BASE).header("Authorization", token)).andExpect(status().isForbidden());
		mvc.perform(post(BASE).header("Authorization", token)).andExpect(status().isForbidden());
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversations", Long.class));
	}
}
