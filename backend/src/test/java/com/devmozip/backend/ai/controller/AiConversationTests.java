package com.devmozip.backend.ai.controller;

import com.devmozip.backend.auth.JwtProvider;
import com.devmozip.backend.auth.RefreshTokenStore;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiConversationTests {
	private static final String BASE = "/api/v1/ai/conversations";
	private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;
	@MockitoBean RefreshTokenStore refreshTokens;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> SECRET);
		registry.add("auth.bootstrap.user-password", () -> SECRET);
		registry.add("auth.bootstrap.admin-password", () -> SECRET);
		registry.add("spring.datasource.url", () -> "jdbc:h2:mem:ai-crud;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
		registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
		registry.add("spring.datasource.username", () -> "sa");
		registry.add("spring.datasource.password", () -> "");
		registry.add("spring.sql.init.mode", () -> "always");
		registry.add("spring.sql.init.schema-locations", () -> "classpath:ai/schema.sql");
		registry.add("management.health.redis.enabled", () -> "false");
		registry.add("ai.current-user.development-enabled", () -> "true");
		registry.add("ai.current-user.test-user-id", () -> "101");
	}

	@BeforeEach void reset() {
		jdbc.update("DELETE FROM ai_recommendations");
		jdbc.update("DELETE FROM ai_recommendation_requests");
		jdbc.update("DELETE FROM ai_messages");
		jdbc.update("DELETE FROM ai_conversations");
		jdbc.update("DELETE FROM ai_usages");
		jdbc.update("DELETE FROM users");
		jdbc.update("INSERT INTO users(id) VALUES (101), (202)");
	}
	private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
		return request.header("Authorization", "Bearer " + jwt.createAccessToken("ai-test", "USER"));
	}
	private long create(String title) throws Exception {
		String body = mvc.perform(authenticated(post(BASE)).contentType("application/json")
			.content(mapper.writeValueAsString(Map.of("title", title))))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return mapper.readTree(body).get("id").asLong();
	}
	private long message(long id, String content) throws Exception {
		String body = mvc.perform(authenticated(post(BASE + "/" + id + "/messages"))
			.contentType("application/json").content(mapper.writeValueAsString(Map.of("content", content))))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"))
			.andReturn().getResponse().getContentAsString();
		return mapper.readTree(body).get("id").asLong();
	}
	private void otherConversation(long id) {
		jdbc.update("INSERT INTO ai_conversations(id,user_id,title,created_at,updated_at) "
			+ "VALUES (?,202,'other',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id);
	}
	@Test void createAndListOnlyOwnConversationsNewestFirst() throws Exception {
		long first = create("first"), second = create("second");
		otherConversation(9001);
		jdbc.update("UPDATE ai_conversations SET created_at = TIMESTAMP WITHOUT TIME ZONE '2030-01-01 00:00:00' WHERE id=?", first);
		mvc.perform(authenticated(get(BASE))).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(first)).andExpect(jsonPath("$[1].id").value(second));
		jdbc.update("UPDATE ai_conversations SET created_at = TIMESTAMP WITHOUT TIME ZONE '2026-01-01 00:00:00' WHERE user_id=101");
		mvc.perform(authenticated(get(BASE))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].id").value(second)).andExpect(jsonPath("$[1].id").value(first));
		mvc.perform(authenticated(post(BASE))).andExpect(status().isCreated());
	}
	@Test void messagesAreStoredInOrderAndNeverGenerateAssistantReplies() throws Exception {
		long id = create("team");
		long first = message(id, "first"), second = message(id, "second");
		jdbc.update("UPDATE ai_messages SET created_at = TIMESTAMP WITHOUT TIME ZONE '2026-01-01 00:00:00' WHERE conversation_id=?", id);
		mvc.perform(authenticated(get(BASE + "/" + id))).andExpect(status().isOk())
			.andExpect(jsonPath("$.messages.length()").value(2))
			.andExpect(jsonPath("$.messages[0].id").value(first))
			.andExpect(jsonPath("$.messages[0].createdAt").value("2026-01-01T00:00:00"))
			.andExpect(jsonPath("$.messages[1].id").value(second))
			.andExpect(jsonPath("$.messages[1].content").value("second"));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_messages WHERE role='ASSISTANT'", Integer.class));
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ai_messages WHERE conversation_id=?", Integer.class, id));
	}
	@Test void otherOwnersAndMissingIdsCannotBeReadWrittenOrDeleted() throws Exception {
		otherConversation(9001);
		for (long id : new long[]{9001, 9002}) {
			mvc.perform(authenticated(get(BASE + "/" + id))).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
			mvc.perform(authenticated(delete(BASE + "/" + id))).andExpect(status().isNotFound());
			mvc.perform(authenticated(post(BASE + "/" + id + "/messages")).contentType("application/json")
				.content("{\"content\":\"forbidden\"}")).andExpect(status().isNotFound());
		}
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversations", Integer.class));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_messages", Integer.class));
	}
	@Test void detailPreservesStoredConditionsAndCanReadExistingAssistantMessages() throws Exception {
		long id = create("existing");
		jdbc.update("UPDATE ai_conversations SET current_conditions = ? FORMAT JSON WHERE id=?",
			"{\"stacks\":[\"Java\"]}", id);
		jdbc.update("INSERT INTO ai_messages(conversation_id,role,content,created_at) VALUES (?,'ASSISTANT','stored reply',CURRENT_TIMESTAMP)", id);
		mvc.perform(authenticated(get(BASE + "/" + id))).andExpect(status().isOk())
			.andExpect(jsonPath("$.currentConditions.stacks[0]").value("Java"))
			.andExpect(jsonPath("$.messages[0].role").value("ASSISTANT"));
	}
	@Test void anonymousAndMissingDevelopmentUsersAreRejected() throws Exception {
		mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
		mvc.perform(post(BASE)).andExpect(status().isUnauthorized());
		jdbc.update("DELETE FROM users WHERE id=101");
		mvc.perform(authenticated(post(BASE))).andExpect(status().isUnauthorized());
	}
	@Test void invalidInputDoesNotWriteDataOrAllowRoleSpoofing() throws Exception {
		long id = create("test");
		mvc.perform(authenticated(post(BASE)).contentType("application/json")
			.content(mapper.writeValueAsString(Map.of("title", "a".repeat(256)))))
			.andExpect(status().isBadRequest());
		for (String body : new String[]{"{}", "{\"content\":null}", "{\"content\":\"  \"}"}) {
			mvc.perform(authenticated(post(BASE + "/" + id + "/messages")).contentType("application/json")
				.content(body)).andExpect(status().isBadRequest());
		}
		mvc.perform(authenticated(get(BASE + "/not-a-number"))).andExpect(status().isBadRequest());
		mvc.perform(authenticated(get(BASE + "/-1"))).andExpect(status().isBadRequest());
		mvc.perform(authenticated(post(BASE + "/" + id + "/messages")).contentType("application/json")
			.content("{\"content\":\"hello\",\"role\":\"ASSISTANT\",\"userId\":202}"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"));
	}
	@Test void hardDeleteRemovesAllChildrenWithoutRestoringUsageOrTouchingOthers() throws Exception {
		long id = create("delete"), mid = message(id, "request");
		otherConversation(9001);
		jdbc.update("INSERT INTO ai_recommendation_requests(id,ai_conversation_id,ai_message_id) VALUES (11,?,?),(12,?,NULL),(13,NULL,?),(14,9001,NULL)", id, mid, id, mid);
		jdbc.update("INSERT INTO ai_recommendations(id,recommended_request_id) VALUES (21,11),(22,12),(23,13),(24,14)");
		jdbc.update("INSERT INTO ai_usages(id,user_id,usage_month,used_count) VALUES (1,101,'2026-11',7)");
		mvc.perform(authenticated(delete(BASE + "/" + id))).andExpect(status().isNoContent());
		for (String table : new String[]{"ai_conversations", "ai_recommendation_requests", "ai_recommendations"}) {
			assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
		}
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_messages", Integer.class));
		assertEquals(7, jdbc.queryForObject("SELECT used_count FROM ai_usages WHERE id=1", Integer.class));
	}
	@Test void foreignKeyConflictRollsBackAllChildDeletes() throws Exception {
		long id = create("rollback"), mid = message(id, "retain");
		jdbc.update("INSERT INTO ai_recommendation_requests(id,ai_conversation_id,ai_message_id) VALUES (31,?,?)", id, mid);
		jdbc.update("INSERT INTO ai_recommendations(id,recommended_request_id) VALUES (41,31)");
		jdbc.execute("CREATE TABLE deletion_blocker (conversation_id BIGINT REFERENCES ai_conversations(id))");
		try {
			jdbc.update("INSERT INTO deletion_blocker(conversation_id) VALUES (?)", id);
			mvc.perform(authenticated(delete(BASE + "/" + id))).andExpect(status().isServiceUnavailable());
			assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversations WHERE id=?", Integer.class, id));
			assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_messages WHERE id=?", Integer.class, mid));
			assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_recommendation_requests WHERE id=31", Integer.class));
			assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_recommendations WHERE id=41", Integer.class));
		} finally {
			jdbc.execute("DROP TABLE deletion_blocker");
		}
	}
}
