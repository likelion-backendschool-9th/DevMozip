package com.devmozip.backend;

import com.devmozip.backend.auth.JwtProvider;
import com.devmozip.backend.auth.RefreshTokenStore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(BackendApplicationTests.Endpoints.class)
class BackendApplicationTests {
	private static final String PASSWORD = UUID.randomUUID().toString();
	private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
	@Autowired MockMvc mvc;
	@Autowired JwtProvider jwt;
	@Autowired ObjectMapper mapper;
	@MockitoBean RefreshTokenStore store;
	private final Map<String, String> tokens = new ConcurrentHashMap<>();

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> SECRET);
		registry.add("auth.bootstrap.user-password", () -> PASSWORD);
		registry.add("auth.bootstrap.admin-password", () -> PASSWORD);
		registry.add("spring.datasource.url", () -> "jdbc:h2:mem:auth;MODE=PostgreSQL");
		registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
		registry.add("spring.datasource.username", () -> "sa");
		registry.add("spring.datasource.password", () -> "");
		registry.add("management.health.redis.enabled", () -> "false");
	}

	@BeforeEach
	void memoryTokens() {
		tokens.clear();
		doAnswer(call -> { tokens.put(call.getArgument(0), call.getArgument(1)); return null; }).when(store).save(anyString(), anyString());
		when(store.matches(anyString(), anyString())).thenAnswer(call -> call.getArgument(1).equals(tokens.get(call.getArgument(0))));
		doAnswer(call -> { tokens.remove(call.getArgument(0), call.getArgument(1)); return null; }).when(store).removeMatching(anyString(), anyString());
	}

	@Test void contextLoads() { assertNotNull(jwt); }
	@Test void anonymousReceivesJson401() throws Exception {
		mvc.perform(get("/api/private/test")).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.path").value("/api/private/test"));
	}
	@Test void userCannotAccessAdmin() throws Exception {
		mvc.perform(get("/api/admin/test").header("Authorization", "Bearer " + jwt.createAccessToken("user", "USER")))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403)).andExpect(jsonPath("$.message").exists());
	}
	@Test void adminCanAccessAdmin() throws Exception {
		mvc.perform(get("/api/admin/test").header("Authorization", "Bearer " + jwt.createAccessToken("admin", "ADMIN"))).andExpect(status().isOk());
	}
	@Test void refreshCannotAuthenticateOrHaveRole() throws Exception {
		String refresh = jwt.createRefreshToken("user");
		assertNull(jwt.getRole(refresh));
		mvc.perform(get("/api/private/test").header("Authorization", "Bearer " + refresh)).andExpect(status().isUnauthorized());
	}
	@Test void expiredAndTamperedTokensAreRejected() {
		var expired = new JwtProvider(new com.devmozip.backend.auth.JwtProperties(SECRET, -1000, 1209600000));
		assertFalse(jwt.validateToken(expired.createAccessToken("user", "USER")));
		String access = jwt.createAccessToken("user", "USER");
		String[] parts = access.split("\\.");
		assertFalse(jwt.validateToken(parts[0] + "." + parts[1] + "." + "A".repeat(parts[2].length())));
	}
	@Test void accessTokenCannotBeRefreshed() throws Exception {
		String access = jwt.createAccessToken("user", "USER");
		mvc.perform(post("/api/auth/refresh").contentType("application/json").content(mapper.writeValueAsString(Map.of("refreshToken", access))))
			.andExpect(status().isUnauthorized());
	}
	@Test void malformedTokenIsRejected() throws Exception {
		assertFalse(jwt.validateToken("invalid"));
		mvc.perform(get("/api/private/test").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
	}
	@Test void loginRefreshLogoutRevokesRefresh() throws Exception {
		String body = mapper.writeValueAsString(Map.of("username", "member@devmozip.demo", "password", PASSWORD));
		String response = mvc.perform(post("/api/auth/login").contentType("application/json").content(body))
			.andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer")).andReturn().getResponse().getContentAsString();
		String refresh = mapper.readTree(response).get("refreshToken").asString();
		String refreshBody = mapper.writeValueAsString(Map.of("refreshToken", refresh));
		mvc.perform(post("/api/auth/refresh").contentType("application/json").content(refreshBody)).andExpect(status().isOk());
		mvc.perform(post("/api/auth/logout").contentType("application/json").content(refreshBody)).andExpect(status().isNoContent());
		mvc.perform(post("/api/auth/refresh").contentType("application/json").content(refreshBody)).andExpect(status().isUnauthorized());
	}
	@Test void wrongPasswordAndStoreFailureAreSanitized() throws Exception {
		mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"member@devmozip.demo\",\"password\":\"incorrect\"}"))
			.andExpect(status().isUnauthorized());
		String refresh = jwt.createRefreshToken("user");
		when(store.matches(anyString(), anyString())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("internal address"));
		mvc.perform(post("/api/auth/refresh").contentType("application/json").content(mapper.writeValueAsString(Map.of("refreshToken", refresh))))
			.andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("잠시 후 다시 시도해 주세요."));
	}

	@TestConfiguration
	static class Endpoints {
		@Bean TestEndpoints testEndpoints() { return new TestEndpoints(); }
	}
	@RestController
	static class TestEndpoints {
		@GetMapping({"/api/private/test", "/api/admin/test"})
		Map<String, String> ok() { return Map.of("status", "ok"); }
	}
}
