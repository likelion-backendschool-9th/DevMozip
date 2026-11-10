package com.devmozip.backend.ai.controller;

import com.devmozip.backend.client.GeminiAnalysisClient;
import com.devmozip.backend.dto.ai.GeminiAnalysis;
import com.devmozip.backend.dto.ai.GeminiAnalysis.*;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.service.AiRecommendationService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiMessageAnalysisTests {
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;
	@MockitoBean RefreshTokenStore refreshTokens;
	@MockitoBean GeminiAnalysisClient gemini;
	@MockitoBean AiRecommendationService engine;
	static final String BASE = "/api/v1/ai/conversations";
	static final String COMPLETE = "{\"interests\":[\"웹\"],\"career\":\"경력 무관\",\"durationMonths\":3}";
	@DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
		r.add("jwt.secret", () -> "analysis-integration-test-signing-secret-12345678901234567890");
		r.add("spring.datasource.url", () -> "jdbc:h2:mem:ai-analysis;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
		r.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
		r.add("spring.datasource.username", () -> "sa");
		r.add("spring.datasource.password", () -> "");
		r.add("spring.sql.init.mode", () -> "always");
		r.add("spring.sql.init.schema-locations", () -> "classpath:ai/schema.sql");
		r.add("management.health.redis.enabled", () -> "false");
		r.add("ai.current-user.development-enabled", () -> "true");
		r.add("ai.current-user.test-user-id", () -> "101");
	}
	@BeforeEach void reset() {
		jdbc.update("DELETE FROM ai_recommendations"); jdbc.update("DELETE FROM ai_recommendation_requests");
		jdbc.update("DELETE FROM ai_usages");
		jdbc.update("DELETE FROM ai_messages"); jdbc.update("DELETE FROM ai_conversations"); jdbc.update("DELETE FROM users");
		jdbc.update("INSERT INTO users(id) VALUES (101),(202)");
		when(gemini.analyze(anyString())).thenReturn(new GeminiAnalysis("어떤 프로젝트를 원하시나요?", List.of("원하는 기간은 얼마인가요?"), List.of(), List.of()));
	}
	MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) { return b.header("Authorization", "Bearer " + jwt.createAccessToken("ai-test", "USER")); }
	long create() throws Exception {
		return mapper.readTree(mvc.perform(auth(post(BASE))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();
	}
	MockHttpServletRequestBuilder send(long id, String key, String text) {
		return auth(post(BASE + "/" + id + "/messages")).contentType("application/json")
			.content(mapper.writeValueAsString(Map.of("content", text, "analyze", true, "clientKey", key)));
	}
	void conditions(long id, String json) throws Exception { mvc.perform(auth(put(BASE + "/" + id + "/conditions")).contentType("application/json").content(json)).andExpect(status().isOk()); }
	Change change(Field field, Action action, List<String> values, Integer count, String evidence) {
		return new Change(field, action, values, count, Origin.EXPLICIT, evidence, true);
	}
	void analysis(List<Change> changes, List<FactChange> facts) {
		doReturn(new GeminiAnalysis("말씀하신 요구사항을 정리했습니다.", List.of(), changes, facts)).when(gemini).analyze(anyString());
	}
	org.springframework.test.web.servlet.ResultActions draft(long id, String conditions) throws Exception {
		return mvc.perform(auth(post(BASE + "/sync")).contentType("application/json")
			.content("{\"conversationId\":" + id + ",\"clientKey\":\"partial-draft\",\"messages\":[],\"conditions\":" + conditions + "}"));
	}
	@Test void partialManualSelectionsReachAnalysisAndAreNotOverwrittenByDescriptivePreferences() throws Exception {
		long id = create();
		draft(id, "{\"stacks\":[\" java \"],\"roles\":[{\"name\":\"백엔드\",\"count\":2}]}")
			.andExpect(status().isOk()).andExpect(jsonPath("$.currentConditions.stacks[0]").value("Java"))
			.andExpect(jsonPath("$.currentConditions.career").doesNotExist())
			.andExpect(jsonPath("$.currentConditions.durationMonths").doesNotExist());
		when(gemini.analyze(anyString())).thenAnswer(call -> {
			var context = mapper.readTree((String) call.getArgument(0)).path("currentConditions");
			assertEquals("Java", context.path("stacks").get(0).asText());
			assertEquals(2, context.path("roles").get(0).path("count").asInt());
			assertFalse(context.has("interests")); assertFalse(context.has("career")); assertFalse(context.has("durationMonths"));
			return new GeminiAnalysis("기존 선택을 유지하고 변경 여부를 확인할게요.", List.of(),
				List.of(new Change(Field.stacks, Action.SET, List.of("Python"), null, Origin.EXPLICIT, "Python", false)), List.of());
		});
		mvc.perform(send(id, "partial", "Python도 좋아 보여")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.currentConditions.stacks[0]").value("Java"))
			.andExpect(jsonPath("$.analysis.suggestions[0].field").value("stacks"))
			.andExpect(jsonPath("$.readyForRecommendation").value(false));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ai_recommendation_requests", Integer.class));
		verifyNoInteractions(engine);
	}
	@Test void missingRoleCountsBecomeConversationQuestionsAndFollowUpCompletesThem() throws Exception {
		long id = create();
		var response = mapper.readValue("""
			{"reply":"커뮤니티를 함께 만들 동료를 찾고 계시는군요.","questions":[],"facts":[],"changes":[
			{"field":"roles","action":"SET","values":["프론트엔드"],"count":null,"origin":"EXPLICIT","evidence":"프론트엔드","explicitChange":false},
			{"field":"roles","action":"SET","values":["백엔드"],"count":null,"origin":"EXPLICIT","evidence":"백엔드","explicitChange":false}]}
			""", GeminiAnalysis.class);
		when(gemini.analyze(anyString())).thenReturn(response);
		mvc.perform(send(id, "no-counts", "커뮤니티를 만들 프론트엔드와 백엔드 동료를 찾고 있어요"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.currentConditions.roles").doesNotExist())
			.andExpect(jsonPath("$.analysis.questions[0]").value("프론트엔드 동료는 몇 명 정도 함께하길 원하시나요?"))
			.andExpect(jsonPath("$.analysis.questions[1]").value("백엔드 동료는 몇 명 정도 함께하길 원하시나요?"))
			.andExpect(jsonPath("$.currentConditions._analysis.facts[0].value").value("프론트엔드 (인원 미정)"));
		analysis(List.of(new Change(Field.roles, Action.SET, List.of("프론트엔드"), 1, Origin.EXPLICIT, "각각 1명", false),
			new Change(Field.roles, Action.SET, List.of("백엔드"), 1, Origin.EXPLICIT, "각각 1명", false)), List.of());
		mvc.perform(send(id, "counts", "각각 1명"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.currentConditions.roles[0].count").value(1))
			.andExpect(jsonPath("$.currentConditions.roles[1].count").value(1))
			.andExpect(jsonPath("$.analysis.questions").isEmpty())
			.andExpect(jsonPath("$.currentConditions._analysis.facts").isEmpty());
		assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM ai_messages", Integer.class));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ai_recommendation_requests", Integer.class));
		verifyNoInteractions(engine);
	}
	@Test void missingCountKeepsExistingRoleCountAndOtherValidChanges() throws Exception {
		long id = create();
		conditions(id, "{\"interests\":[\"웹\"],\"roles\":[{\"name\":\"백엔드\",\"count\":2}],\"career\":\"경력 무관\",\"durationMonths\":3}");
		analysis(List.of(change(Field.roles, Action.SET, List.of("백엔드"), null, "백엔드"),
			change(Field.stacks, Action.ADD, List.of("React"), null, "React")), List.of());
		mvc.perform(send(id, "keep-count", "React도 추가하고 백엔드 동료를 찾고 있어요"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.currentConditions.roles[0].count").value(2))
			.andExpect(jsonPath("$.currentConditions.stacks[0]").value("React"))
			.andExpect(jsonPath("$.analysis.questions[0]").exists());
		verifyNoInteractions(engine);
	}
	@Test void roleCountQuestionAppearsOnlyOnceAcrossReplyQuestionsAndServerFallback() throws Exception {
		long id = create();
		String question = "프론트엔드와 백엔드는 각각 몇 명 정도 생각하고 계세요?";
		when(gemini.analyze(anyString())).thenReturn(new GeminiAnalysis("함께 만들 동료를 찾고 계시는군요! " + question,
			List.of(question), List.of(change(Field.roles, Action.SET, List.of("프론트엔드"), null, "프론트엔드"),
				change(Field.roles, Action.SET, List.of("백엔드"), null, "백엔드")), List.of()));
		String json = mvc.perform(send(id, "one-question", "프론트엔드와 백엔드 동료를 찾고 있어요"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.analysis.questions.length()").value(1))
			.andExpect(jsonPath("$.currentConditions.roles").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		String reply = mapper.readTree(json).path("assistantMessage").path("content").asText();
		assertEquals(1, reply.split(java.util.regex.Pattern.quote(question), -1).length - 1);
		assertFalse(reply.contains("확인할 내용"));
		assertFalse(reply.contains("동료는 몇 명"));
		verifyNoInteractions(engine);
	}
	@Test void appliedConditionsShowOnlyFinalValueOnceForEveryField() throws Exception {
		long id = create();
		String text = "웹 커뮤니티에 React Spring Boot로 프론트엔드 백엔드 디자이너 각각 1명, 경력 무관에서 1년 이상, 3개월에서 6개월로 정리해줘";
		analysis(List.of(change(Field.roles, Action.ADD, List.of("프론트엔드"), 1, text),
			change(Field.roles, Action.ADD, List.of("백엔드"), 1, text),
			change(Field.roles, Action.ADD, List.of("디자이너"), 1, text),
			change(Field.interests, Action.ADD, List.of("웹"), null, text),
			change(Field.interests, Action.ADD, List.of("커뮤니티"), null, text),
			change(Field.stacks, Action.ADD, List.of("React"), null, text),
			change(Field.stacks, Action.ADD, List.of("Spring Boot"), null, text),
			change(Field.stacks, Action.ADD, List.of("React"), null, text),
			change(Field.career, Action.SET, List.of("경력 무관"), null, text),
			change(Field.career, Action.SET, List.of("1년 이상"), null, text),
			change(Field.durationMonths, Action.SET, List.of("3"), null, text),
			change(Field.durationMonths, Action.SET, List.of("6"), null, text)), List.of());
		mvc.perform(send(id, "final-summary", text)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.analysis.appliedChanges.length()").value(5))
			.andExpect(jsonPath("$.analysis.appliedChanges[0]").value("모집 직군: 프론트엔드 1명, 백엔드 1명, 디자이너 1명"))
			.andExpect(jsonPath("$.analysis.appliedChanges[1]").value("관심 분야: 웹, 커뮤니티"))
			.andExpect(jsonPath("$.analysis.appliedChanges[2]").value("요구 기술: React, Spring Boot"))
			.andExpect(jsonPath("$.analysis.appliedChanges[3]").value("요구 경력: 1년 이상"))
			.andExpect(jsonPath("$.analysis.appliedChanges[4]").value("예상 기간: 6개월"));
		verifyNoInteractions(engine);
	}
	@Test void confirmsActualStoredRequirementsAndReplacementWithoutRunningRecommendation() throws Exception {
		long id = create();
		analysis(List.of(), List.of(new FactChange(Kind.TARGET_EXPERIENCE, Action.ADD, "결제 구현 경험", Origin.EXPLICIT, "결제 구현 경험")));
		mvc.perform(send(id, "payment-requirement", "결제 구현 경험이 있는 개발자를 선호해요"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.assistantMessage.content").value(org.hamcrest.Matchers.containsString("요구사항: 결제 구현 경험")));
		analysis(List.of(), List.of(new FactChange(Kind.TARGET_EXPERIENCE, Action.SET, "실시간 채팅 구현 경험", Origin.EXPLICIT, "실시간 채팅 구현 경험")));
		String json = mvc.perform(send(id, "chat-requirement", "결제 경험 선호는 취소하고 실시간 채팅 구현 경험이 있는 개발자를 선호해요"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String reply = mapper.readTree(json).path("assistantMessage").path("content").asText();
		assertTrue(reply.contains("요구사항: 실시간 채팅 구현 경험"));
		assertTrue(reply.contains("다음 추천을 실행하면 평가에 반영"));
		assertTrue(reply.contains("기존 추천 결과는 변경되지 않습니다"));
		assertFalse(reply.contains("요구사항: 결제 구현 경험"));
		analysis(List.of(), List.of(new FactChange(Kind.TARGET_EXPERIENCE, Action.CLEAR, "모두 취소", Origin.EXPLICIT, "모두 취소")));
		mvc.perform(send(id, "clear-requirements", "모두 취소해줘"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.assistantMessage.content").value(org.hamcrest.Matchers.containsString("저장된 세부 요구사항: 없음")));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ai_recommendation_requests", Integer.class));
		verifyNoInteractions(engine);
	}
	@Test void ownAndInferredFactsAreNotConfirmedAsScoringRequirements() throws Exception {
		long id = create();
		analysis(List.of(), List.of(new FactChange(Kind.OWN_SKILL, Action.ADD, "React", Origin.EXPLICIT, "React"),
			new FactChange(Kind.TARGET_EXPERIENCE, Action.ADD, "멘토링", Origin.INFERRED, "초보")));
		String json = mvc.perform(send(id, "not-target", "React를 공부하는 초보예요"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		assertFalse(mapper.readTree(json).path("assistantMessage").path("content").asText().contains("저장된 세부 요구사항"));
		verifyNoInteractions(engine);
	}
	@Test void appliedConditionsOmitCancelledChangesAndShowFinalClearOnce() throws Exception {
		long id = create();
		conditions(id, "{\"interests\":[\"웹\"],\"stacks\":[\"React\"],\"career\":\"경력 무관\",\"durationMonths\":3}");
		String text = "관심 분야에 커뮤니티 추가했다가 다시 삭제하고 기술은 Java로 바꿨다가 모두 해제해줘";
		analysis(List.of(change(Field.interests, Action.ADD, List.of("커뮤니티"), null, text),
			change(Field.interests, Action.REMOVE, List.of("커뮤니티"), null, text),
			change(Field.stacks, Action.SET, List.of("Java"), null, text),
			change(Field.stacks, Action.CLEAR, List.of(), null, text)), List.of());
		mvc.perform(send(id, "clear-summary", text)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.analysis.appliedChanges.length()").value(1))
			.andExpect(jsonPath("$.analysis.appliedChanges[0]").value("요구 기술: 선택 해제"))
			.andExpect(jsonPath("$.currentConditions.interests[0]").value("웹"))
			.andExpect(jsonPath("$.currentConditions.stacks").doesNotExist());
		verifyNoInteractions(engine);
	}
	@Test void emptyDraftClearsSelectionsWithoutErasingFactsOrRequiringCompleteConditions() throws Exception {
		long id = create(); conditions(id, COMPLETE);
		analysis(List.of(), List.of(new FactChange(Kind.FEATURE, Action.ADD, "로그인", Origin.EXPLICIT, "로그인")));
		mvc.perform(send(id, "fact", "로그인을 만들고 싶어")).andExpect(status().isCreated());
		draft(id, "{\"interests\":[],\"stacks\":[],\"roles\":[],\"career\":null,\"durationMonths\":null}")
			.andExpect(status().isOk()).andExpect(jsonPath("$.currentConditions.interests").doesNotExist())
			.andExpect(jsonPath("$.currentConditions.career").doesNotExist())
			.andExpect(jsonPath("$.currentConditions.durationMonths").doesNotExist())
			.andExpect(jsonPath("$.currentConditions._analysis.facts[0].value").value("로그인"));
		analysis(List.of(), List.of());
		mvc.perform(send(id, "empty", "편하게 얘기하고 싶어")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.analysis.questions").isEmpty()).andExpect(jsonPath("$.readyForRecommendation").value(false));
		String error = mvc.perform(auth(post(BASE + "/" + id + "/recommendations")).header("Idempotency-Key", "partial-missing"))
			.andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
		String message = mapper.readTree(error).path("message").asText();
		assertTrue(message.contains("관심 분야")); assertTrue(message.contains("경력")); assertTrue(message.contains("몇 개월"));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ai_recommendation_requests", Integer.class));
		verifyNoInteractions(engine);
		mvc.perform(auth(put(BASE + "/" + id + "/conditions")).contentType("application/json").content("{}"))
			.andExpect(status().isBadRequest());
	}
	@Test void partialDraftStillRejectsInvalidValuesAndMetadataBeforeWriting() throws Exception {
		long id = create(); conditions(id, COMPLETE);
		for (String invalid : List.of("{\"interests\":[\"invalid\"]}", "{\"interests\":[true]}",
			"{\"career\":\"\"}", "{\"durationMonths\":\"3\"}", "{\"durationMonths\":1.5}",
			"{\"durationMonths\":0}", "{\"durationMonths\":37}", "{\"durationMonths\":true}",
			"{\"roles\":[{\"name\":\"백엔드\",\"count\":0}]}", "{\"roles\":[null]}",
			"{\"stacks\":[\"\"]}", "{\"_analysis\":{\"facts\":[]}}", "{\"extra\":true}")) {
			draft(id, invalid).andExpect(status().isBadRequest());
		}
		mvc.perform(auth(get(BASE + "/" + id))).andExpect(jsonPath("$.currentConditions.durationMonths").value(3));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ai_messages", Integer.class));
		verifyNoInteractions(gemini, engine);
	}
	@Test void partialDraftSurvivesProviderFailureAndSameMessageRetryWithoutDefaults() throws Exception {
		long id = create(); draft(id, "{\"durationMonths\":6}").andExpect(status().isOk());
		when(gemini.analyze(anyString())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "분석 불가"));
		mvc.perform(send(id, "partial-retry", "선택한 기간을 참고해줘")).andExpect(status().isServiceUnavailable());
		mvc.perform(auth(get(BASE + "/" + id))).andExpect(jsonPath("$.currentConditions.durationMonths").value(6))
			.andExpect(jsonPath("$.currentConditions.interests").doesNotExist());
		analysis(List.of(), List.of());
		mvc.perform(send(id, "partial-retry", "선택한 기간을 참고해줘")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.currentConditions.durationMonths").value(6))
			.andExpect(jsonPath("$.currentConditions.career").doesNotExist());
		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM ai_messages", Integer.class));
		verifyNoInteractions(engine);
	}
	@Test void extractsStructuredIntentAndOwnSkillsWithoutConvertingThemToTargetSkills() throws Exception {
		long id = create();
		String text = "초보 개발자야. Java와 Spring, PostgreSQL을 할 줄 알고 JavaScript를 배우고 있어. 결제 경험자와 배우며 웹 프로젝트에 참여하고 로그인, 회원가입을 1개월 동안 구현하고 싶어. 주제는 상관없어.";
		analysis(List.of(change(Field.interests, Action.ADD, List.of("웹"), null, "웹 프로젝트"),
			change(Field.durationMonths, Action.SET, List.of("1"), null, "1개월")), List.of(
			new FactChange(Kind.OWN_SKILL, Action.ADD, "Java, Spring, PostgreSQL", Origin.EXPLICIT, "Java와 Spring, PostgreSQL을 할 줄 알고"),
			new FactChange(Kind.LEARNING_SKILL, Action.ADD, "JavaScript", Origin.EXPLICIT, "JavaScript를 배우고 있어"),
			new FactChange(Kind.PURPOSE, Action.ADD, "프로젝트 참여", Origin.EXPLICIT, "프로젝트에 참여"),
			new FactChange(Kind.TARGET_EXPERIENCE, Action.ADD, "결제 기능 구현 경험", Origin.EXPLICIT, "결제 경험자"),
			new FactChange(Kind.FEATURE, Action.ADD, "로그인, 회원가입", Origin.EXPLICIT, "로그인, 회원가입"),
			new FactChange(Kind.LEARNING_GOAL, Action.ADD, "협업하며 학습", Origin.EXPLICIT, "경험자와 배우며"),
			new FactChange(Kind.USER_LEVEL, Action.ADD, "초보 개발자", Origin.EXPLICIT, "초보 개발자"),
			new FactChange(Kind.PROJECT_PREFERENCE, Action.ADD, "주제 제한 없음", Origin.EXPLICIT, "주제는 상관없어")));
		mvc.perform(send(id, "first", text)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.currentConditions.interests[0]").value("웹"))
			.andExpect(jsonPath("$.currentConditions.durationMonths").value(1))
			.andExpect(jsonPath("$.currentConditions.stacks").doesNotExist())
			.andExpect(jsonPath("$.currentConditions.roles").doesNotExist())
			.andExpect(jsonPath("$.currentConditions.career").doesNotExist())
			.andExpect(jsonPath("$.analysis.facts.length()").value(8))
			.andExpect(jsonPath("$.assistantMessage.role").value("ASSISTANT"));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ai_recommendation_requests", Integer.class));
		verifyNoInteractions(engine);
	}
	@Test void ambiguousOrInferredChangesDoNotOverwriteManualConditions() throws Exception {
		long id = create(); conditions(id, COMPLETE);
		analysis(List.of(new Change(Field.durationMonths, Action.SET, List.of("6"), null, Origin.INFERRED, "장기", false)), List.of());
		mvc.perform(send(id, "inferred", "장기 프로젝트면 좋겠어")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.currentConditions.durationMonths").value(3))
			.andExpect(jsonPath("$.analysis.suggestions[0].origin").value("INFERRED"))
			.andExpect(jsonPath("$.analysis.questions[0]").exists());
		analysis(List.of(new Change(Field.durationMonths, Action.SET, List.of("6"), null, Origin.EXPLICIT, "6개월", false)), List.of());
		mvc.perform(send(id, "ambiguous", "6개월도 좋아 보여")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.currentConditions.durationMonths").value(3));
	}
	@Test void explicitAddModifyRemoveAndClearAreApplied() throws Exception {
		long id = create(); conditions(id, "{\"interests\":[\"웹\"],\"stacks\":[\"Java\"],\"roles\":[{\"name\":\"백엔드\",\"count\":1}],\"career\":\"경력 무관\",\"durationMonths\":3}");
		String command = "백엔드 1명 추가, 기간을 6개월로 변경, Java 삭제, 경력 조건 삭제";
		analysis(List.of(change(Field.roles, Action.ADD, List.of("백엔드"), 1, "백엔드 1명 추가"),
			change(Field.durationMonths, Action.SET, List.of("6"), null, "기간을 6개월로 변경"),
			change(Field.stacks, Action.REMOVE, List.of("Java"), null, "Java 삭제"),
			change(Field.career, Action.CLEAR, List.of(), null, "경력 조건 삭제")), List.of());
		mvc.perform(send(id, "commands", command)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.currentConditions.roles[0].count").value(2))
			.andExpect(jsonPath("$.currentConditions.durationMonths").value(6))
			.andExpect(jsonPath("$.currentConditions.stacks").doesNotExist())
			.andExpect(jsonPath("$.currentConditions.career").doesNotExist())
			.andExpect(jsonPath("$.readyForRecommendation").value(false));
	}
	@Test void multipleTurnsPreserveDetailsAndContextAndDistinguishRecruitmentPurpose() throws Exception {
		long id = create();
		analysis(List.of(), List.of(new FactChange(Kind.TARGET_EXPERIENCE, Action.ADD, "결제 구현 경험", Origin.EXPLICIT, "결제 경험자")));
		mvc.perform(send(id, "one", "결제 경험자가 필요해")).andExpect(status().isCreated());
		when(gemini.analyze(anyString())).thenAnswer(call -> {
			String context = call.getArgument(0);
			assertTrue(context.contains("결제 구현 경험")); assertTrue(context.contains("결제 경험자가 필요해"));
			assertFalse(context.contains("private@example.com"));
			return new GeminiAnalysis("팀원 모집을 원하시는군요.", List.of("모집 인원은 몇 명인가요?"), List.of(),
				List.of(new FactChange(Kind.PURPOSE, Action.ADD, "팀원 모집", Origin.EXPLICIT, "팀원을 모집")));
		});
		mvc.perform(send(id, "two", "팀원을 모집하고 싶어. private@example.com")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.analysis.facts.length()").value(2)).andExpect(jsonPath("$.analysis.questions[0]").exists());
		mvc.perform(auth(get(BASE + "/" + id))).andExpect(status().isOk())
			.andExpect(jsonPath("$.currentConditions._analysis.facts.length()").value(2))
			.andExpect(jsonPath("$.messages.length()").value(4));
	}
	@Test void successfulReplayDoesNotCallGeminiOrDuplicateMessages() throws Exception {
		long id = create();
		mvc.perform(send(id, "key", "추천 조건을 도와줘")).andExpect(status().isCreated());
		mvc.perform(send(id, "key", "추천 조건을 도와줘")).andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
		verify(gemini, times(1)).analyze(anyString());
		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM ai_messages", Integer.class));
		mvc.perform(send(id, "key", "다른 내용")).andExpect(status().isConflict());
	}
	@Test void providerFailureRetainsUserAndConditionsAndAllowsSameKeyRetry() throws Exception {
		long id = create(); conditions(id, COMPLETE);
		when(gemini.analyze(anyString())).thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "분석 시간 초과"));
		mvc.perform(send(id, "retry", "분석해줘")).andExpect(status().isGatewayTimeout());
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM ai_messages", Integer.class));
		mvc.perform(auth(get(BASE + "/" + id))).andExpect(jsonPath("$.currentConditions.durationMonths").value(3));
		analysis(List.of(), List.of());
		mvc.perform(send(id, "retry", "분석해줘")).andExpect(status().isCreated());
		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM ai_messages", Integer.class));
	}
	@Test void putAndSyncPreserveDetailsAndMetadataCannotBeInjected() throws Exception {
		long id = create();
		analysis(List.of(), List.of(new FactChange(Kind.FEATURE, Action.ADD, "로그인", Origin.EXPLICIT, "로그인")));
		mvc.perform(send(id, "detail", "로그인을 만들고 싶어")).andExpect(status().isCreated());
		conditions(id, COMPLETE);
		mvc.perform(auth(post(BASE + "/sync")).contentType("application/json")
			.content("{\"conversationId\":" + id + ",\"clientKey\":\"draft\",\"messages\":[],\"conditions\":" + COMPLETE + "}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.currentConditions._analysis.facts[0].value").value("로그인"))
			.andExpect(jsonPath("$.currentConditions._analysis.sources.durationMonths.origin").value("MANUAL"));
		mvc.perform(auth(put(BASE + "/" + id + "/conditions")).contentType("application/json")
			.content(COMPLETE.replace("}", ",\"_analysis\":{}}"))).andExpect(status().isBadRequest());
	}
	@Test void invalidGroundingNeverChangesConditionsOrSavesAssistant() throws Exception {
		long id = create(); conditions(id, COMPLETE);
		analysis(List.of(change(Field.durationMonths, Action.SET, List.of("6"), null, "말하지 않은 내용")), List.of());
		mvc.perform(send(id, "invalid", "안녕")).andExpect(status().isBadGateway());
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM ai_messages", Integer.class));
		mvc.perform(auth(get(BASE + "/" + id))).andExpect(jsonPath("$.currentConditions.durationMonths").value(3));
	}
	@Test void concurrentManualEditIsNotOverwritten() throws Exception {
		long id = create(); conditions(id, COMPLETE);
		when(gemini.analyze(anyString())).thenAnswer(call -> {
			jdbc.update("UPDATE ai_conversations SET current_conditions=? FORMAT JSON WHERE id=?", COMPLETE.replace(":3", ":12"), id);
			return new GeminiAnalysis("확인했어요.", List.of(), List.of(), List.of());
		});
		mvc.perform(send(id, "stale", "도와줘")).andExpect(status().isConflict());
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM ai_messages", Integer.class));
	}
	@Test void ownershipAuthenticationAndMissingKeysAreCheckedBeforeProviderCall() throws Exception {
		long id = create();
		mvc.perform(post(BASE + "/" + id + "/messages").contentType("application/json").content("{\"content\":\"test\",\"analyze\":true,\"clientKey\":\"a\"}")).andExpect(status().isUnauthorized());
		jdbc.update("UPDATE ai_conversations SET user_id=202 WHERE id=?", id);
		mvc.perform(send(id, "key", "안녕")).andExpect(status().isNotFound());
		mvc.perform(send(999999, "key", "안녕")).andExpect(status().isNotFound());
		long own = create();
		mvc.perform(auth(post(BASE + "/" + own + "/messages")).contentType("application/json")
			.content("{\"content\":\"안녕\",\"analyze\":true}")).andExpect(status().isBadRequest());
		mvc.perform(auth(post(BASE + "/" + own + "/messages")).contentType("application/json")
			.content("{\"content\":\"안녕\",\"analyze\":\"true\",\"clientKey\":\"invalid-boolean\"}")).andExpect(status().isBadRequest());
		verifyNoInteractions(gemini);
	}
	@Test void recommendationSnapshotsConfirmedRequirementsAndKeepsChatAnalysisSeparate() throws Exception {
		long id = create(); conditions(id, COMPLETE);
		analysis(List.of(), List.of(new FactChange(Kind.TARGET_EXPERIENCE, Action.ADD, "결제 구현 경험", Origin.EXPLICIT, "결제 경험자")));
		mvc.perform(send(id, "intent", "결제 경험자를 원해")).andExpect(status().isCreated());
		when(engine.recommend(any(), any(), any())).thenReturn(new com.devmozip.backend.dto.ai.internal.RecommendationResult(List.of(), List.of(), List.of(), List.of()));
		mvc.perform(auth(post(BASE + "/" + id + "/recommendations")).header("Idempotency-Key", "engine-separation"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.conditions.durationMonths").value(3))
			.andExpect(jsonPath("$.conditions._analysis").doesNotExist());
		verify(engine).recommend(any(com.devmozip.backend.dto.ai.UpdateConditionsRequest.class), isNull(), any());
		mvc.perform(auth(get(BASE + "/" + id + "/recommendations")))
			.andExpect(jsonPath("$.conditions._requirements.facts[0].value").value("결제 구현 경험"));
		conditions(id, COMPLETE.replace(":3", ":6"));
		mvc.perform(auth(get(BASE + "/" + id + "/recommendations")))
			.andExpect(jsonPath("$.conditions.durationMonths").value(3))
			.andExpect(jsonPath("$.conditions._requirements.facts[0].value").value("결제 구현 경험"));
		mvc.perform(auth(post(BASE + "/" + id + "/recommendations")).header("Idempotency-Key", "engine-separation")
			.contentType("application/json").content("{\"replay\":true}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.conditions.durationMonths").value(3));
		verify(engine, times(1)).recommend(any(), any(), any());
		mvc.perform(auth(get(BASE + "/" + id))).andExpect(jsonPath("$.currentConditions._analysis.facts[0].value").value("결제 구현 경험"));
	}
	@Test void providerQuotaAndInvalidResponseFailuresRetainOnlyUserMessages() throws Exception {
		for (HttpStatus failure : List.of(HttpStatus.TOO_MANY_REQUESTS, HttpStatus.BAD_GATEWAY)) {
			long id = create();
			doThrow(new ResponseStatusException(failure, "분석 실패")).when(gemini).analyze(anyString());
			mvc.perform(send(id, "error-" + failure.value(), "도와줘")).andExpect(status().is(failure.value()));
			assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ai_messages WHERE conversation_id=? AND role='ASSISTANT'", Integer.class, id));
		}
		verifyNoInteractions(engine);
	}
	@Test void requirementEvaluationFailureStoresFailedRequestWithoutBasicFallbackOrResults() throws Exception {
		long id = create(); conditions(id, COMPLETE);
		analysis(List.of(), List.of(new FactChange(Kind.TARGET_EXPERIENCE, Action.ADD, "결제 경험", Origin.EXPLICIT, "결제 경험")));
		mvc.perform(send(id, "semantic-failure", "결제 경험이 필요해")).andExpect(status().isCreated());
		when(engine.recommend(any(), any(), any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "invalid evidence"));
		mvc.perform(auth(post(BASE + "/" + id + "/recommendations")).header("Idempotency-Key", "semantic-failure"))
			.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.developers").isEmpty()).andExpect(jsonPath("$.projects").isEmpty());
		mvc.perform(auth(get(BASE + "/" + id + "/recommendations")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ai_recommendations", Integer.class));
		verify(engine, never()).recommend(any(), any());
	}
	@Test void ownSkillEvidenceCannotBeAppliedAsTargetStackEvenWhenModelMisclassifiesIt() throws Exception {
		long id = create();
		analysis(List.of(change(Field.stacks, Action.ADD, List.of("Java"), null, "Java를 할 줄 알아")),
			List.of(new FactChange(Kind.OWN_SKILL, Action.ADD, "Java", Origin.EXPLICIT, "Java를 할 줄 알아")));
		mvc.perform(send(id, "own", "Java를 할 줄 알아")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.currentConditions.stacks").doesNotExist()).andExpect(jsonPath("$.analysis.questions[0]").exists());
	}
	@Test void invalidDomainValuesNeverPersistAsConditions() throws Exception {
		long id = create(); conditions(id, COMPLETE);
		analysis(List.of(change(Field.durationMonths, Action.SET, List.of("37"), null, "37개월")), List.of());
		mvc.perform(send(id, "invalid-duration", "기간을 37개월로 바꿔줘")).andExpect(status().isBadGateway());
		mvc.perform(auth(get(BASE + "/" + id))).andExpect(jsonPath("$.currentConditions.durationMonths").value(3));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ai_messages WHERE role='ASSISTANT'", Integer.class));
	}
	@Test void concurrentSameKeyCommitsOneAssistantAndAppliesRelativeChangeOnce() throws Exception {
		long id = create();
		conditions(id, "{\"interests\":[\"웹\"],\"roles\":[{\"name\":\"백엔드\",\"count\":1}],\"career\":\"경력 무관\",\"durationMonths\":3}");
		var entered = new java.util.concurrent.CountDownLatch(2);
		when(gemini.analyze(anyString())).thenAnswer(call -> {
			entered.countDown();
			assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS));
			return new GeminiAnalysis("명시한 인원 변경을 확인했습니다.", List.of(),
				List.of(change(Field.roles, Action.ADD, List.of("백엔드"), 1, "백엔드 1명 추가")), List.of());
		});
		try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
			java.util.concurrent.Callable<Integer> call = () -> mvc.perform(send(id, "concurrent", "백엔드 1명 추가")).andReturn().getResponse().getStatus();
			var one = pool.submit(call); var two = pool.submit(call);
			var statuses = new ArrayList<>(List.of(one.get(20, java.util.concurrent.TimeUnit.SECONDS), two.get(20, java.util.concurrent.TimeUnit.SECONDS)));
			Collections.sort(statuses);
			assertEquals(List.of(200, 201), statuses);
		}
		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM ai_messages", Integer.class));
		mvc.perform(auth(get(BASE + "/" + id))).andExpect(jsonPath("$.currentConditions.roles[0].count").value(2));
	}
	@Test void ordinaryConversationWithoutAnyRequiredConditionsDoesNotAppendAChecklist() throws Exception {
		long id = create(); analysis(List.of(), List.of());
		mvc.perform(send(id, "free-chat", "개발 초보라 어떤 프로젝트가 좋을지 모르겠어")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.assistantMessage.content").value("말씀하신 요구사항을 정리했습니다."))
			.andExpect(jsonPath("$.analysis.questions").isEmpty())
			.andExpect(jsonPath("$.readyForRecommendation").value(false))
			.andExpect(jsonPath("$.currentConditions.interests").doesNotExist())
			.andExpect(jsonPath("$.currentConditions.career").doesNotExist())
			.andExpect(jsonPath("$.currentConditions.durationMonths").doesNotExist());
		verifyNoInteractions(engine);
	}
	@Test void unrestrictedTopicIsRetainedWithoutInventingInterestsAndOnlyRecommendationValidatesMissingFields() throws Exception {
		long id = create();
		analysis(List.of(change(Field.durationMonths, Action.SET, List.of("1"), null, "1개월")),
			List.of(new FactChange(Kind.PROJECT_PREFERENCE, Action.ADD, "주제 제한 없음", Origin.EXPLICIT, "주제 상관없음")));
		mvc.perform(send(id, "unrestricted", "주제 상관없음. 1개월 동안 배우고 싶어")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.currentConditions.interests").doesNotExist())
			.andExpect(jsonPath("$.currentConditions.durationMonths").value(1))
			.andExpect(jsonPath("$.analysis.questions").isEmpty());
		analysis(List.of(), List.of());
		mvc.perform(send(id, "continue", "초보에게 어울리는 협업 방식은 뭐야?")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.analysis.facts[0].value").value("주제 제한 없음"))
			.andExpect(jsonPath("$.currentConditions.interests").doesNotExist());
		String error = mvc.perform(auth(post(BASE + "/" + id + "/recommendations")).header("Idempotency-Key", "missing-topic"))
			.andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
		String message = mapper.readTree(error).path("message").asText();
		assertTrue(message.contains("관심 분야")); assertTrue(message.contains("경력"));
		assertFalse(message.contains("몇 개월"));
		verify(gemini, times(2)).analyze(anyString());
		verifyNoInteractions(engine);
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ai_recommendation_requests", Integer.class));
		mvc.perform(auth(get(BASE + "/" + id))).andExpect(jsonPath("$.currentConditions._analysis.facts[0].value").value("주제 제한 없음"));
	}
}
