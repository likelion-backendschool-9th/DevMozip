package com.devmozip.backend.ai.controller;

import com.devmozip.backend.entity.*;
import com.devmozip.backend.entity.enums.ProjectJobRole;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.service.AiRecommendationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiRecommendationExecutionTests {
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;
	@Autowired MemberRepository members;
	@Autowired ProjectRepository projects;
	@Autowired AiConversationRepository conversations;
	@Autowired AiMessageRepository messages;
	@Autowired AiRecommendationRequestRepository requestRepository;
	@MockitoBean RefreshTokenStore tokens;
	@MockitoSpyBean AiRecommendationService engine;
	long ownerId, developerId, projectId, conversationId;
	String authorization;
	static final String CONDITIONS = "{\"interests\":[\"교육\"],\"stacks\":[\"Java\"],\"career\":\"3년 이상\",\"durationMonths\":3}";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		String configured = System.getenv("DEVMOZIP_AI_EXECUTION_TEST_JDBC_URL");
		registry.add("spring.datasource.url", () -> configured != null ? configured :
			"jdbc:h2:mem:ai-execution;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON");
		registry.add("spring.datasource.driver-class-name", () -> configured != null ? "org.postgresql.Driver" : "org.h2.Driver");
		registry.add("spring.datasource.username", () -> configured != null ? "ai_engine_test" : "sa");
		registry.add("spring.datasource.password", () -> "");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		registry.add("spring.sql.init.mode", () -> "never");
		registry.add("management.health.redis.enabled", () -> "false");
	}

	@BeforeAll static void productionSchema(@Autowired JdbcTemplate jdbc) {
		if (System.getenv("DEVMOZIP_AI_EXECUTION_TEST_JDBC_URL") != null) {
			jdbc.execute("DROP TABLE ai_recommendations");
			jdbc.execute("DROP TABLE ai_recommendation_requests");
			new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
				new org.springframework.core.io.FileSystemResource("../docs/sql/ai-recommendations.sql"))
				.execute(java.util.Objects.requireNonNull(jdbc.getDataSource()));
		}
	}

	@BeforeEach void setup() {
		reset(engine);
		for (String table : List.of("ai_recommendations", "ai_recommendation_requests", "ai_messages", "ai_conversations", "projects", "users")) jdbc.update("DELETE FROM " + table);
		ownerId = member("owner").getId();
		developerId = member("developer").getId();
		authorization = "Bearer " + jwt.createAccessToken("owner@example.com", "USER");
		var start = LocalDate.of(2026, 1, 31);
		projectId = projects.saveAndFlush(new Project(ownerId, "교육 플랫폼", null, "교육 Java", null, "https://example.com/a.png",
			true, List.of(new ProjectRecruitmentSlot(ProjectJobRole.BACKEND, 1, 3)), List.of("Java"), List.of("교육"),
			start, start, start, start.plusMonths(3), LocalDateTime.now())).getId();
		var conversation = new AiConversation(ownerId, "추천", LocalDateTime.now());
		conversation.replaceConditions(CONDITIONS, LocalDateTime.now());
		conversationId = conversations.saveAndFlush(conversation).getId();
	}
	Member member(String tag) {
		return members.saveAndFlush(Member.builder().email(tag + "@example.com").nickname(tag).tag(tag)
			.status(Member.MemberStatus.ACTIVE).role(Member.MemberRole.USER).collaborationStatus(Member.CollaborationStatus.AVAILABLE)
			.career("3년 차").jobCategories(List.of("백엔드 개발자")).skills(List.of("Java")).interests(List.of("교육"))
			.trustScore(new BigDecimal("50")).build());
	}
	String path() { return "/api/v1/ai/conversations/" + conversationId + "/recommendations"; }
	MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) { return request.header("Authorization", authorization); }
	long execute(String key) throws Exception {
		String body = mvc.perform(auth(post(path())).header("Idempotency-Key", key)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("SUCCESS")).andReturn().getResponse().getContentAsString();
		return mapper.readTree(body).get("requestId").asLong();
	}
	long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }

	@Test void realEngineResultsArePersistedAndGetNeverRecalculates() throws Exception {
		long id = execute("first");
		assertEquals(1, count("ai_recommendation_requests"));
		assertEquals(2, count("ai_recommendations"));
		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM ai_recommendations WHERE detailed_reason IS NULL AND rank=1", Long.class));
		jdbc.update("UPDATE users SET trust_score=36.5 WHERE id=?", developerId);
		clearInvocations(engine);
		mvc.perform(auth(get(path()))).andExpect(status().isOk()).andExpect(jsonPath("$.requestId").value(id))
			.andExpect(jsonPath("$.developers[0].recommendedUserId").value(developerId))
			.andExpect(jsonPath("$.developers[0].matchScore").value(100.0))
			.andExpect(jsonPath("$.developers[0].basicReason").isNotEmpty())
			.andExpect(jsonPath("$.projects[0].recommendedProjectId").value(projectId));
		verifyNoInteractions(engine);
	}

	@Test void snapshotsAndPastRequestsSurviveConditionChangesAndLatestSelectsNewest() throws Exception {
		long old = execute("old");
		mvc.perform(auth(put("/api/v1/ai/conversations/" + conversationId + "/conditions")).contentType("application/json")
			.content(CONDITIONS.replace("3년 이상", "5년 이상"))).andExpect(status().isOk());
		long latest = execute("latest");
		mvc.perform(auth(get(path()))).andExpect(status().isOk()).andExpect(jsonPath("$.requestId").value(latest))
			.andExpect(jsonPath("$.developers").isEmpty());
		mvc.perform(auth(get(path()).param("requestId", String.valueOf(old)))).andExpect(status().isOk())
			.andExpect(jsonPath("$.conditions.career").value("3년 이상")).andExpect(jsonPath("$.developers.length()").value(1));
	}

	@Test void sameKeyReplaysAndDifferentInputConflicts() throws Exception {
		long id = execute("key");
		mvc.perform(auth(post(path())).header("Idempotency-Key", "key")).andExpect(status().isOk()).andExpect(jsonPath("$.requestId").value(id));
		mvc.perform(auth(put("/api/v1/ai/conversations/" + conversationId + "/conditions")).contentType("application/json")
			.content(CONDITIONS.replace("3년 이상", "5년 이상"))).andExpect(status().isOk());
		mvc.perform(auth(post(path())).header("Idempotency-Key", "key")).andExpect(status().isConflict());
		assertEquals(1, count("ai_recommendation_requests"));
		verify(engine, times(1)).recommend(any(), isNull());
	}

	@Test void authenticationOwnershipAndInvalidConditionsDoNotCreateRequests() throws Exception {
		mvc.perform(post(path()).header("Idempotency-Key", "anonymous")).andExpect(status().isUnauthorized());
		mvc.perform(get(path())).andExpect(status().isUnauthorized());
		mvc.perform(auth(post(path()))).andExpect(status().isBadRequest());
		mvc.perform(auth(get(path()))).andExpect(status().isNotFound());
		mvc.perform(auth(get(path()).param("requestId", "0"))).andExpect(status().isBadRequest());
		String otherToken = jwt.createAccessToken("developer@example.com", "USER");
		mvc.perform(post(path()).header("Authorization", "Bearer " + otherToken).header("Idempotency-Key", "other"))
			.andExpect(status().isNotFound());
		mvc.perform(get(path()).header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
		var invalid = conversations.findById(conversationId).orElseThrow();
		invalid.replaceConditions("{}", LocalDateTime.now());
		conversations.saveAndFlush(invalid);
		mvc.perform(auth(post(path())).header("Idempotency-Key", "invalid")).andExpect(status().isBadRequest());
		assertEquals(0, count("ai_recommendation_requests"));
		verifyNoInteractions(engine);
	}

	@Test void onlyUserMessagesFromSameConversationCanBeLinked() throws Exception {
		var other = conversations.saveAndFlush(new AiConversation(ownerId, "other", LocalDateTime.now()));
		long foreignMessage = messages.saveAndFlush(new AiMessage(other, "다른 대화", LocalDateTime.now())).getId();
		mvc.perform(auth(post(path())).header("Idempotency-Key", "foreign").contentType("application/json")
			.content("{\"messageId\":" + foreignMessage + "}")).andExpect(status().isBadRequest());
		var own = conversations.findById(conversationId).orElseThrow();
		long message = messages.saveAndFlush(new AiMessage(own, "교육 플랫폼", LocalDateTime.now())).getId();
		mvc.perform(auth(post(path())).header("Idempotency-Key", "message").contentType("application/json")
			.content("{\"messageId\":" + message + "}")).andExpect(status().isCreated()).andExpect(jsonPath("$.messageId").value(message));
		verify(engine).recommend(any(), eq("교육 플랫폼"));
	}

	@Test void engineFailureIsDurablyRecordedAndSameKeyDoesNotRetry() throws Exception {
		doThrow(new IllegalStateException("test failure")).when(engine).recommend(any(), any());
		String body = mvc.perform(auth(post(path())).header("Idempotency-Key", "fail")).andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.status").value("FAILED")).andReturn().getResponse().getContentAsString();
		long id = mapper.readTree(body).get("requestId").asLong();
		mvc.perform(auth(get(path()).param("requestId", String.valueOf(id)))).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.completedAt").isNotEmpty());
		mvc.perform(auth(post(path())).header("Idempotency-Key", "fail")).andExpect(status().isInternalServerError());
		assertEquals(0, count("ai_recommendations"));
		verify(engine, times(1)).recommend(any(), any());
	}

	@Test void invalidTargetDuringPersistenceRollsBackAllResultsAndKeepsFailedRequest() throws Exception {
		var score = new com.devmozip.backend.dto.ai.internal.RecommendationResult.Score(new BigDecimal("50"), List.of(), List.of("기본 이유"));
		var invalid = new com.devmozip.backend.dto.ai.internal.RecommendationResult(
			List.of(new com.devmozip.backend.dto.ai.internal.RecommendationResult.Developer(developerId, "valid", "valid", List.of(), score)),
			List.of(new com.devmozip.backend.dto.ai.internal.RecommendationResult.SimilarProject(Long.MAX_VALUE, "missing", score, null)), List.of(), List.of());
		doReturn(invalid).when(engine).recommend(any(), any());
		mvc.perform(auth(post(path())).header("Idempotency-Key", "bad-target")).andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.status").value("FAILED"));
		assertEquals(0, count("ai_recommendations"));
		assertEquals(1, count("ai_recommendation_requests"));
	}

	@Test void databaseRejectsBothOrNeitherTargetsAndMissingForeignKeys() throws Exception {
		long request = execute("constraints");
		String sql = "INSERT INTO ai_recommendations(recommended_request_id,recommended_user_id,recommended_project_id,match_score,basic_reason,rank,created_at) VALUES (?,?,?,50,'reason',2,CURRENT_TIMESTAMP)";
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, request, null, null));
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, request, ownerId, projectId));
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, request, Long.MAX_VALUE, null));
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, Long.MAX_VALUE, ownerId, null));
	}

	@Test void deleteRemovesRecommendationHistoryAndRollsBackOnForeignKeyBlocker() throws Exception {
		long request = execute("delete");
		jdbc.execute("CREATE TABLE execution_delete_blocker (conversation_id BIGINT REFERENCES ai_conversations(id))");
		try {
			jdbc.update("INSERT INTO execution_delete_blocker VALUES (?)", conversationId);
			mvc.perform(auth(delete("/api/v1/ai/conversations/" + conversationId))).andExpect(status().isInternalServerError());
			assertEquals(2, count("ai_recommendations"));
			mvc.perform(auth(get(path()).param("requestId", String.valueOf(request)))).andExpect(status().isOk());
			jdbc.update("DELETE FROM execution_delete_blocker");
			mvc.perform(auth(delete("/api/v1/ai/conversations/" + conversationId))).andExpect(status().isNoContent());
			assertEquals(0, count("ai_recommendations"));
			assertEquals(0, count("ai_recommendation_requests"));
		} finally { jdbc.execute("DROP TABLE execution_delete_blocker"); }
	}

	@Test void concurrentSameKeyCreatesOneRequestAndOneResultSet() throws Exception {
		try (var executor = Executors.newFixedThreadPool(2)) {
			var task = (java.util.concurrent.Callable<Integer>) () -> mvc.perform(auth(post(path())).header("Idempotency-Key", "concurrent"))
				.andReturn().getResponse().getStatus();
			var responses = executor.invokeAll(List.of(task, task));
			for (var response : responses) assertTrue(List.of(200, 201, 202).contains(response.get()));
		}
		assertEquals(1, count("ai_recommendation_requests"));
		assertEquals(2, count("ai_recommendations"));
		verify(engine, times(1)).recommend(any(), any());
	}
	@Test void pendingReplayDoesNotExecuteAndEmptyCandidatesStillSucceed() throws Exception {
		var conversation = conversations.findById(conversationId).orElseThrow();
		// Persist via ORM so both H2 and PostgreSQL receive object JSON rather than a JSON string.
		long pending = requestRepository.saveAndFlush(new AiRecommendationRequest(conversation, null, CONDITIONS, "pending")).getId();
		mvc.perform(auth(post(path())).header("Idempotency-Key", "pending")).andExpect(status().isAccepted())
			.andExpect(jsonPath("$.requestId").value(pending)).andExpect(jsonPath("$.status").value("PENDING"));
		verifyNoInteractions(engine);
		jdbc.update("UPDATE users SET collaboration_status='BUSY'");
		jdbc.update("UPDATE projects SET is_public=false");
		long empty = execute("empty");
		mvc.perform(auth(get(path()).param("requestId", String.valueOf(empty)))).andExpect(status().isOk())
			.andExpect(jsonPath("$.developers").isEmpty()).andExpect(jsonPath("$.projects").isEmpty());
	}

	@Test void rejectsCoercedMessageIdsUnknownFieldsAndOtherConversationRequest() throws Exception {
		for (String body : List.of("{\"messageId\":1.5}", "{\"messageId\":\"1\"}", "{\"messageId\":true}",
			"{\"messageId\":0}", "{\"userId\":1}")) {
			mvc.perform(auth(post(path())).header("Idempotency-Key", "invalid-body").contentType("application/json")
				.content(body)).andExpect(status().isBadRequest());
		}
		long request = execute("own");
		var other = conversations.saveAndFlush(new AiConversation(ownerId, "other", LocalDateTime.now()));
		mvc.perform(auth(get("/api/v1/ai/conversations/" + other.getId() + "/recommendations")
			.param("requestId", String.valueOf(request)))).andExpect(status().isNotFound());
	}

	@Test void postgresProductionSchemaEnforcesUniqueRanksAndNumericBounds() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("DEVMOZIP_AI_EXECUTION_TEST_JDBC_URL") != null, "PostgreSQL partial index validation");
		long request = execute("sql-constraints");
		String sql = "INSERT INTO ai_recommendations(recommended_request_id,recommended_user_id,match_score,basic_reason,rank,created_at) VALUES (?,? ,?,'reason',?,CURRENT_TIMESTAMP)";
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, request, ownerId, 50, 1));
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, request, ownerId, 101, 2));
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, request, ownerId, 50, 0));
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM users WHERE id=?", developerId));
	}

}
