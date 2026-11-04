package com.devmozip.backend.ai.controller;

import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
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
	private static final String MINIMUM_CONDITIONS = "{\"fields\":[\"웹\"],\"career\":\"경력 무관\",\"durationMonths\":3}";
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
		jdbc.update("DELETE FROM ai_messages");
		jdbc.update("DELETE FROM ai_conversations");
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
	@Test void deleteRemovesOnlyOwnedConversationAndItsMessagesWithoutRecommendationTables() throws Exception {
		long id = create("delete"), mid = message(id, "first");
		message(id, "second");
		long retained = create("retain");
		long retainedMessage = message(retained, "retain");
		otherConversation(9001);
		jdbc.update("INSERT INTO ai_messages(conversation_id,role,content,created_at) VALUES (9001,'USER','other',CURRENT_TIMESTAMP)");
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
			+ "WHERE table_name IN ('AI_RECOMMENDATIONS','AI_RECOMMENDATION_REQUESTS','AI_USAGES')", Integer.class));
		mvc.perform(authenticated(delete(BASE + "/" + id))).andExpect(status().isNoContent());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversations WHERE id=?", Integer.class, id));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_messages WHERE conversation_id=?", Integer.class, id));
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversations", Integer.class));
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ai_messages", Integer.class));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_messages WHERE id=?", Integer.class, retainedMessage));
		mvc.perform(authenticated(get(BASE + "/" + id))).andExpect(status().isNotFound());
		mvc.perform(authenticated(delete(BASE + "/" + id))).andExpect(status().isNotFound());
	}
	@Test void emptyConversationCanBeDeletedAndAnonymousDeletionIsRejected() throws Exception {
		long id = create("empty");
		mvc.perform(delete(BASE + "/" + id)).andExpect(status().isUnauthorized());
		mvc.perform(authenticated(get(BASE + "/" + id))).andExpect(status().isOk());
		mvc.perform(authenticated(delete(BASE + "/" + id))).andExpect(status().isNoContent());
	}
	@Test void foreignKeyConflictRollsBackAllChildDeletes() throws Exception {
		long id = create("rollback"), mid = message(id, "retain");
		jdbc.execute("CREATE TABLE deletion_blocker (conversation_id BIGINT REFERENCES ai_conversations(id))");
		try {
			jdbc.update("INSERT INTO deletion_blocker(conversation_id) VALUES (?)", id);
			mvc.perform(authenticated(delete(BASE + "/" + id))).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.status").value(500));
			assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversations WHERE id=?", Integer.class, id));
			assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_messages WHERE id=?", Integer.class, mid));
		} finally {
			jdbc.execute("DROP TABLE deletion_blocker");
		}
	}

	@Test void savesAllConditionsAsJsonAndUpdatesOnlyTheOwnedConversation() throws Exception {
		long id = create("conditions"), retained = create("retained");
		message(id, "keep this message");
		jdbc.update("UPDATE ai_conversations SET updated_at=TIMESTAMP '2020-01-01 00:00:00' WHERE id=?", id);
		String body = """
			{"fields":["웹","AI / 머신러닝"],"interests":["생산성","AI"],
			 "stacks":["Java","Spring Boot"],"roles":[{"name":"백엔드 개발자","count":2}],
			 "career":"3년 이상","durationMonths":3}
			""";
		String response = mvc.perform(authenticated(put(BASE + "/" + id + "/conditions"))
			.contentType("application/json").content(body)).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.currentConditions.roles[0].count").value(2))
			.andReturn().getResponse().getContentAsString();
		var stored = mapper.readTree(jdbc.queryForObject("SELECT current_conditions FROM ai_conversations WHERE id=?", String.class, id));
		assertEquals(mapper.readTree(body), stored);
		assertEquals(stored, mapper.readTree(response).get("currentConditions"));
		assertEquals(jdbc.queryForObject("SELECT updated_at FROM ai_conversations WHERE id=?", java.time.LocalDateTime.class, id),
			java.time.LocalDateTime.parse(mapper.readTree(response).get("updatedAt").asText()));
		assertTrue(java.time.LocalDateTime.parse(mapper.readTree(response).get("updatedAt").asText())
			.isAfter(java.time.LocalDateTime.of(2020, 1, 1, 0, 0)));
		mvc.perform(authenticated(get(BASE + "/" + id))).andExpect(status().isOk())
			.andExpect(jsonPath("$.currentConditions.career").value("3년 이상"))
			.andExpect(jsonPath("$.messages[0].content").value("keep this message"));
		mvc.perform(authenticated(get(BASE + "/" + retained))).andExpect(status().isOk())
			.andExpect(jsonPath("$.currentConditions").isEmpty());
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_messages", Integer.class));
	}

	private String withRequiredConditions(String body) {
		var conditions = (tools.jackson.databind.node.ObjectNode) mapper.readTree(body);
		if (!conditions.has("fields")) conditions.putArray("fields").add("웹");
		if (!conditions.has("career")) conditions.put("career", "경력 무관");
		if (!conditions.has("durationMonths")) conditions.put("durationMonths", 3);
		return mapper.writeValueAsString(conditions);
	}

	@Test void putRemovesOptionalConditionsWhileKeepingRequiredFieldsCareerAndDuration() throws Exception {
		long id = create("replace");
		String[] bodies = {
			"{\"fields\":[\"웹\"],\"stacks\":[\"Java\"],\"career\":\"경력 무관\",\"durationMonths\":36}",
			withRequiredConditions("{\"stacks\":[\"React\"]}"), MINIMUM_CONDITIONS,
			withRequiredConditions("{\"interests\":null,\"stacks\":[],\"roles\":null}")
		};
		for (int i = 0; i < bodies.length; i++) {
			String response = mvc.perform(authenticated(put(BASE + "/" + id + "/conditions"))
				.contentType("application/json").content(bodies[i])).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
			var expected = mapper.readTree(i == 3 ? MINIMUM_CONDITIONS : bodies[i]);
			assertEquals(expected, mapper.readTree(response).get("currentConditions"));
			String detail = mvc.perform(authenticated(get(BASE + "/" + id))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
			assertEquals(expected, mapper.readTree(detail).get("currentConditions"));
		}
	}

	@Test void missingOrNullCareerAndDurationRejectUpdatesWithoutChangingSavedData() throws Exception {
		long id = create("required");
		mvc.perform(authenticated(put(BASE + "/" + id + "/conditions"))
			.contentType("application/json").content(MINIMUM_CONDITIONS)).andExpect(status().isOk());
		jdbc.update("UPDATE ai_conversations SET updated_at=TIMESTAMP '2020-01-01 00:00:00' WHERE id=?", id);
		for (String body : new String[]{"{}", "{\"career\":\"경력 무관\"}", "{\"durationMonths\":3}",
			"{\"career\":null,\"durationMonths\":3}", "{\"career\":\"경력 무관\",\"durationMonths\":null}",
			"{\"career\":null,\"durationMonths\":null}"}) {
			var conditions = (tools.jackson.databind.node.ObjectNode) mapper.readTree(body);
			conditions.putArray("fields").add("웹");
			body = mapper.writeValueAsString(conditions);
			mvc.perform(authenticated(put(BASE + "/" + id + "/conditions"))
				.contentType("application/json").content(body)).andExpect(status().isBadRequest());
		}
		assertEquals(mapper.readTree(MINIMUM_CONDITIONS), mapper.readTree(jdbc.queryForObject(
			"SELECT current_conditions FROM ai_conversations WHERE id=?", String.class, id)));
		assertEquals(java.time.LocalDateTime.of(2020, 1, 1, 0, 0), jdbc.queryForObject(
			"SELECT updated_at FROM ai_conversations WHERE id=?", java.time.LocalDateTime.class, id));
	}

	@Test void initialAndLegacyNullConditionsReadAsEmptyObjectsWithoutBackfillingRows() throws Exception {
		long id = create("empty");
		assertEquals(mapper.readTree("{}"), mapper.readTree(jdbc.queryForObject(
			"SELECT current_conditions FROM ai_conversations WHERE id=?", String.class, id)));
		jdbc.update("UPDATE ai_conversations SET current_conditions=NULL WHERE id=?", id);
		mvc.perform(authenticated(get(BASE + "/" + id))).andExpect(status().isOk())
			.andExpect(jsonPath("$.currentConditions").isMap()).andExpect(jsonPath("$.currentConditions").isEmpty());
		assertNull(jdbc.queryForObject("SELECT current_conditions FROM ai_conversations WHERE id=?", String.class, id));
	}

	@Test void choicesAreTrimmedAndDeduplicatedButDuplicateRolesAreRejected() throws Exception {
		long id = create("normalize");
		mvc.perform(authenticated(put(BASE + "/" + id + "/conditions")).contentType("application/json")
			.content(withRequiredConditions("{\"fields\":[\" 웹 \",\"웹\"],\"interests\":[\"AI\",\"AI\"],\"stacks\":[\" Java \",\"Java\"],\"roles\":[{\"name\":\" 백엔드 개발자 \",\"count\":1}],\"career\":\" 경력 무관 \"}")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.currentConditions.fields.length()").value(1))
			.andExpect(jsonPath("$.currentConditions.interests.length()").value(1))
			.andExpect(jsonPath("$.currentConditions.stacks[0]").value("Java"))
			.andExpect(jsonPath("$.currentConditions.roles[0].name").value("백엔드 개발자"));
		mvc.perform(authenticated(put(BASE + "/" + id + "/conditions")).contentType("application/json")
			.content(withRequiredConditions("{\"roles\":[{\"name\":\"백엔드 개발자\",\"count\":1},{\"name\":\" 백엔드 개발자 \",\"count\":2}]}")))
			.andExpect(status().isBadRequest());
	}

	@Test void invalidConditionTypesCountsAndChoicesNeverChangeStoredConditionsOrTime() throws Exception {
		long id = create("invalid");
		jdbc.update("UPDATE ai_conversations SET updated_at=TIMESTAMP '2020-01-01 00:00:00' WHERE id=?", id);
		String[] invalid = {
			"", "null", "[]", "{", "{\"fields\":\"웹\"}", "{\"fields\":[null]}", "{\"fields\":[\" \"]}",
			"{\"interests\":[\"unknown\"]}", "{\"stacks\":[\" \"]}", "{\"career\":\"신입\"}",
			"{\"career\":0}", "{\"career\":\"\"}", "{\"roles\":[null]}", "{\"roles\":[{}]}",
			"{\"roles\":[{\"name\":\"백엔드\",\"count\":1}]}", "{\"roles\":[{\"name\":\"백엔드 개발자\"}]}",
			"{\"durationMonths\":0}", "{\"durationMonths\":37}", "{\"durationMonths\":1.5}",
			"{\"durationMonths\":\"3\"}", "{\"durationMonths\":true}", "{\"userId\":202}",
			"{\"roles\":[{\"name\":\"백엔드 개발자\",\"count\":1,\"extra\":1}]}"
		};
		for (String body : invalid) {
			if (body.startsWith("{") && body.endsWith("}")) body = withRequiredConditions(body);
			mvc.perform(authenticated(put(BASE + "/" + id + "/conditions"))
				.contentType("application/json").content(body)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
		}
		for (String count : new String[]{"0", "-1", "1.5", "1.0", "\"2\"", "null", "true", "2147483648"}) {
			mvc.perform(authenticated(put(BASE + "/" + id + "/conditions")).contentType("application/json")
				.content(withRequiredConditions("{\"roles\":[{\"name\":\"백엔드 개발자\",\"count\":" + count + "}]}")))
				.andExpect(status().isBadRequest());
		}
		assertEquals(mapper.readTree("{}"), mapper.readTree(jdbc.queryForObject(
			"SELECT current_conditions FROM ai_conversations WHERE id=?", String.class, id)));
		assertEquals(java.time.LocalDateTime.of(2020, 1, 1, 0, 0), jdbc.queryForObject(
			"SELECT updated_at FROM ai_conversations WHERE id=?", java.time.LocalDateTime.class, id));
	}

	@Test void acceptsCustomFieldsAndStacksAndNormalizesKnownSkillLabels() throws Exception {
		long id = create("custom");
		String response = mvc.perform(authenticated(put(BASE + "/" + id + "/conditions"))
			.contentType("application/json").content(withRequiredConditions(
				"""
				{"fields":[" 해양 탐사 ","해양 탐사"],"stacks":[" java ","Java"," Vue ","Vue","Docker"]}
				""")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.currentConditions.fields.length()").value(1))
			.andExpect(jsonPath("$.currentConditions.fields[0]").value("해양 탐사"))
			.andExpect(jsonPath("$.currentConditions.stacks.length()").value(3))
			.andExpect(jsonPath("$.currentConditions.stacks[0]").value("Java"))
			.andExpect(jsonPath("$.currentConditions.stacks[1]").value("Vue"))
			.andExpect(jsonPath("$.currentConditions.stacks[2]").value("Docker"))
			.andReturn().getResponse().getContentAsString();
		assertEquals(mapper.readTree(response).get("currentConditions"), mapper.readTree(jdbc.queryForObject(
			"SELECT current_conditions FROM ai_conversations WHERE id=?", String.class, id)));
		mvc.perform(authenticated(get(BASE + "/" + id))).andExpect(status().isOk())
			.andExpect(jsonPath("$.currentConditions.stacks[1]").value("Vue"));
	}

	@Test void enforcesRequiredFieldAndCustomInputBoundsWithoutChangingSavedConditions() throws Exception {
		long id = create("bounds");
		mvc.perform(authenticated(put(BASE + "/" + id + "/conditions"))
			.contentType("application/json").content(MINIMUM_CONDITIONS)).andExpect(status().isOk());
		jdbc.update("UPDATE ai_conversations SET updated_at=TIMESTAMP '2020-01-01 00:00:00' WHERE id=?", id);
		var missingField = (tools.jackson.databind.node.ObjectNode) mapper.readTree(MINIMUM_CONDITIONS);
		missingField.remove("fields");
		mvc.perform(authenticated(put(BASE + "/" + id + "/conditions")).contentType("application/json")
			.content(mapper.writeValueAsString(missingField))).andExpect(status().isBadRequest());
		String tooManyFields = mapper.writeValueAsString(java.util.stream.IntStream.range(0, 11).mapToObj(i -> "분야" + i).toList());
		String tooManyStacks = mapper.writeValueAsString(java.util.stream.IntStream.range(0, 31).mapToObj(i -> "기술" + i).toList());
		for (String body : new String[]{"{}", "{\"fields\":null}", "{\"fields\":[]}", "{\"fields\":[null]}",
			"{\"fields\":[\" \"]}", "{\"fields\":[123]}", "{\"fields\":[true]}",
			"{\"fields\":[\"" + "가".repeat(31) + "\"]}", "{\"fields\":" + tooManyFields + "}",
			"{\"stacks\":[null]}", "{\"stacks\":[\"\"]}", "{\"stacks\":[123]}", "{\"stacks\":[true]}",
			"{\"stacks\":[\"" + "s".repeat(51) + "\"]}", "{\"stacks\":" + tooManyStacks + "}"}) {
			mvc.perform(authenticated(put(BASE + "/" + id + "/conditions"))
				.contentType("application/json").content(body.equals("{}") ? body : withRequiredConditions(body)))
				.andExpect(status().isBadRequest());
		}
		assertEquals(mapper.readTree(MINIMUM_CONDITIONS), mapper.readTree(jdbc.queryForObject(
			"SELECT current_conditions FROM ai_conversations WHERE id=?", String.class, id)));
		assertEquals(java.time.LocalDateTime.of(2020, 1, 1, 0, 0), jdbc.queryForObject(
			"SELECT updated_at FROM ai_conversations WHERE id=?", java.time.LocalDateTime.class, id));
		String boundary = withRequiredConditions(mapper.writeValueAsString(Map.of(
			"fields", java.util.stream.IntStream.range(0, 10).mapToObj(i -> "가".repeat(29) + i).toList(),
			"stacks", java.util.stream.IntStream.range(0, 30).mapToObj(i -> "s".repeat(48) + String.format("%02d", i)).toList())));
		mvc.perform(authenticated(put(BASE + "/" + id + "/conditions")).contentType("application/json").content(boundary))
			.andExpect(status().isOk()).andExpect(jsonPath("$.currentConditions.fields.length()").value(10))
			.andExpect(jsonPath("$.currentConditions.stacks.length()").value(30));
	}

	@Test void conditionUpdatesEnforceAuthenticationOwnershipAndValidIds() throws Exception {
		long id = create("owner");
		otherConversation(9001);
		mvc.perform(put(BASE + "/" + id + "/conditions").contentType("application/json").content(MINIMUM_CONDITIONS))
			.andExpect(status().isUnauthorized());
		for (long denied : new long[]{9001, 999999}) {
			mvc.perform(authenticated(put(BASE + "/" + denied + "/conditions"))
				.contentType("application/json").content(withRequiredConditions("{\"stacks\":[\"Java\"]}")))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
		}
		for (String invalidId : new String[]{"0", "-1", "bad-id"}) {
			mvc.perform(authenticated(put(BASE + "/" + invalidId + "/conditions"))
				.contentType("application/json").content(MINIMUM_CONDITIONS))
				.andExpect(status().isBadRequest());
		}
		assertNull(jdbc.queryForObject("SELECT current_conditions FROM ai_conversations WHERE id=9001", String.class));
	}
}
