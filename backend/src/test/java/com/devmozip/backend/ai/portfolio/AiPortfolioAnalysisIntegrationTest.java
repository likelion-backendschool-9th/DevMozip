package com.devmozip.backend.ai.portfolio;

import com.devmozip.backend.client.GeminiAnalysisClient;
import com.devmozip.backend.entity.*;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.service.AiPortfolioAnalysisService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:ai-portfolio;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
	"spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "management.health.redis.enabled=false"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class AiPortfolioAnalysisIntegrationTest {
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper mapper;
	@Autowired JwtProvider jwt;
	@Autowired MemberRepository members;
	@Autowired AiConversationRepository conversations;
	@Autowired AiRecommendationRequestRepository requests;
	@Autowired AiRecommendationRepository recommendations;
	@Autowired AiPortfolioAnalysisRepository analysis;
	@MockitoBean RefreshTokenStore tokens;
	@MockitoBean GeminiAnalysisClient gemini;
	long owner, developer, outsider, recommendation;
	String authorization;
	LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Seoul")); }
	String path() { return "/api/v1/ai/recommendations/" + recommendation + "/detailed-analysis"; }
	Member member(String tag, Member.MemberRole role) {
		return members.saveAndFlush(Member.builder().email(tag + "@test.local").nickname(tag).tag(tag).role(role)
			.status(Member.MemberStatus.ACTIVE).collaborationStatus(Member.CollaborationStatus.AVAILABLE)
			.jobCategories(List.of("백엔드 개발자")).skills(List.of("Java")).build());
	}
	@BeforeEach void setup() {
		reset(gemini);
		jdbc.execute("CREATE TABLE IF NOT EXISTS portfolios(id BIGINT PRIMARY KEY,user_id BIGINT,title VARCHAR(255),summary TEXT,role VARCHAR(100),tech_stack JSONB,description TEXT,visibility VARCHAR(20),updated_at TIMESTAMP)");
		// 이 클래스 전용 H2 fixture를 사용해 공통 구독 엔티티의 DDL과 분리한다.
		jdbc.execute("DROP TABLE IF EXISTS subscriptions CASCADE");
		jdbc.execute("CREATE TABLE IF NOT EXISTS subscriptions(id BIGINT PRIMARY KEY,user_id BIGINT,status VARCHAR(30),starts_at TIMESTAMP,current_period_end TIMESTAMP,ended_at TIMESTAMP)");
		for (String table : List.of("portfolios", "subscriptions", "ai_recommendations", "ai_recommendation_requests", "ai_messages", "ai_conversations", "users")) jdbc.update("DELETE FROM " + table);
		owner = member("premium", Member.MemberRole.PREMIUM).getId();
		developer = member("candidate", Member.MemberRole.USER).getId();
		outsider = member("outsider", Member.MemberRole.PREMIUM).getId();
		authorization = "Bearer " + jwt.createAccessToken("premium@test.local", "PREMIUM");
		subscription("ACTIVE", now().minusDays(1), now().plusDays(1), null);
		var conversation = conversations.saveAndFlush(new AiConversation(owner, "추천", now()));
		String snapshot = mapper.writeValueAsString(Map.of("interests", List.of("핀테크"), "stacks", List.of("Java"),
			"career", "경력 무관", "durationMonths", 3, "_requirements", Map.of("version", 1, "facts", List.of(
				Map.of("kind", "TARGET_EXPERIENCE", "value", "결제 구현 경험", "origin", "EXPLICIT", "evidence", "결제 구현 경험", "messageId", 1)))));
		var request = new AiRecommendationRequest(conversation, null, snapshot, "recommend");
		request.complete(AiRecommendationRequest.Status.SUCCESS);
		request = requests.saveAndFlush(request);
		recommendation = recommendations.saveAndFlush(new AiRecommendation(request, members.findById(developer).orElseThrow(), null,
			new BigDecimal("80.00"), "기존 기본 이유", 1)).getId();
		portfolio(1, "결제 시스템", "결제 구현 경험", "PUBLIC", "주문별 결제 요청의 멱등성을 구현했습니다.");
		success();
	}
	void subscription(String status, LocalDateTime start, LocalDateTime end, LocalDateTime ended) {
		jdbc.update("DELETE FROM subscriptions WHERE user_id=?", owner);
		jdbc.update("INSERT INTO subscriptions VALUES(1,?,?,?,?,?)", owner, status, start, end, ended);
	}
	void portfolio(long id, String title, String summary, String visibility, String body) {
		jdbc.update("INSERT INTO portfolios VALUES(?,?,?,?,?,?,?,?,?)", id, developer, title, summary, "백엔드", "[\"Java\"]", body, visibility, now());
	}
	void success() {
		doAnswer(call -> {
			assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
			var input = mapper.readTree((String) call.getArgument(0));
			var rows = mapper.createArrayNode();
			for (var requirement : input.path("requirements")) {
				rows.add(mapper.valueToTree(Map.of("requirementId", requirement.path("id").asText(), "status", "MET",
					"portfolioId", 1, "field", "description", "quote", "멱등성을 구현했습니다")));
			}
			return mapper.createObjectNode().set("assessments", rows);
		}).when(gemini).generate(anyString(), anyString(), any());
	}
	String generate() throws Exception {
		return mvc.perform(post(path()).header("Authorization", authorization).contentType("application/json").content("{}"))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}
	@Test void onlyOwnerWithCurrentPremiumRoleAndValidSubscriptionCanGenerateOrRead() throws Exception {
		mvc.perform(post(path())).andExpect(status().isUnauthorized());
		mvc.perform(get(path())).andExpect(status().isUnauthorized());
		mvc.perform(post(path()).header("Authorization", "Bearer " + jwt.createAccessToken("outsider@test.local", "PREMIUM")))
			.andExpect(status().isNotFound());
		jdbc.update("UPDATE users SET role='USER' WHERE id=?", owner);
		mvc.perform(post(path()).header("Authorization", authorization)).andExpect(status().isForbidden());
		jdbc.update("UPDATE users SET role='PREMIUM' WHERE id=?", owner);
		jdbc.update("DELETE FROM subscriptions");
		mvc.perform(get(path()).header("Authorization", authorization)).andExpect(status().isForbidden());
		mvc.perform(post(path()).header("Authorization", authorization)).andExpect(status().isForbidden());
		jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", owner);
		mvc.perform(post(path()).header("Authorization", authorization)).andExpect(status().isForbidden());
		verifyNoInteractions(gemini);
	}
	@ParameterizedTest @ValueSource(strings = {"EXPIRED", "CANCELED", "UNKNOWN"})
	void inactiveSubscriptionStatusBlocksEvenWithFutureEnd(String statusValue) throws Exception {
		subscription(statusValue, now().minusDays(1), now().plusDays(1), null);
		mvc.perform(post(path()).header("Authorization", authorization)).andExpect(status().isForbidden());
		verifyNoInteractions(gemini);
	}
	@Test void expirationFutureStartAndEndedAtAreCheckedAndCancellationUntilEndRemainsValid() throws Exception {
		for (var range : List.of(List.of(now().minusDays(2), now().minusSeconds(1)), List.of(now().plusDays(1), now().plusDays(2)))) {
			subscription("ACTIVE", range.get(0), range.get(1), null);
			mvc.perform(post(path()).header("Authorization", authorization)).andExpect(status().isForbidden());
		}
		subscription("ACTIVE", now().minusDays(1), now().plusDays(1), now().minusSeconds(1));
		mvc.perform(post(path()).header("Authorization", authorization)).andExpect(status().isForbidden());
		subscription("CANCEL_REQUESTED", now().minusDays(1), now().plusDays(1), null);
		assertEquals("READY", mapper.readTree(generate()).path("status").asText());
	}
	@Test void validAnalysisCachesGroundedQuotesAndNeverChangesRecommendationScoresOrCounts() throws Exception {
		var first = mapper.readTree(generate());
		assertEquals("READY", first.path("status").asText());
		assertFalse(first.path("cached").asBoolean());
		assertEquals("MET", first.path("assessments").get(0).path("status").asText());
		clearInvocations(gemini);
		mvc.perform(get(path()).header("Authorization", authorization)).andExpect(status().isOk())
			.andExpect(jsonPath("$.cached").value(true)).andExpect(jsonPath("$.assessments[0].quote").value("멱등성을 구현했습니다"));
		generate(); verifyNoInteractions(gemini);
		assertEquals(new BigDecimal("80.00"), jdbc.queryForObject("SELECT match_score FROM ai_recommendations WHERE id=?", BigDecimal.class, recommendation));
		assertEquals("기존 기본 이유", jdbc.queryForObject("SELECT basic_reason FROM ai_recommendations WHERE id=?", String.class, recommendation));
		assertEquals(1L, recommendations.count()); assertEquals(1L, requests.count());
	}
	@Test void cachedAnalysisIsBlockedAfterRoleExpiryPrivateDeletionOrTargetSuspension() throws Exception {
		generate(); clearInvocations(gemini);
		subscription("ACTIVE", now().minusDays(1), now().minusSeconds(1), null);
		mvc.perform(get(path()).header("Authorization", authorization)).andExpect(status().isForbidden());
		subscription("ACTIVE", now().minusDays(1), now().plusDays(1), null);
		jdbc.update("UPDATE users SET role='USER' WHERE id=?", owner);
		mvc.perform(get(path()).header("Authorization", authorization)).andExpect(status().isForbidden());
		jdbc.update("UPDATE users SET role='PREMIUM' WHERE id=?", owner);
		jdbc.update("UPDATE portfolios SET visibility='PRIVATE'");
		String hidden = mvc.perform(get(path()).header("Authorization", authorization)).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("STALE")).andExpect(jsonPath("$.assessments").isEmpty()).andReturn().getResponse().getContentAsString();
		assertFalse(hidden.contains("멱등성"));
		jdbc.update("DELETE FROM portfolios");
		mvc.perform(get(path()).header("Authorization", authorization)).andExpect(status().isOk()).andExpect(jsonPath("$.assessments").isEmpty());
		jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", developer);
		mvc.perform(get(path()).header("Authorization", authorization)).andExpect(status().isNotFound());
		verifyNoInteractions(gemini);
	}
	@Test void changedBodyWithoutTimestampChangeInvalidatesAndRegeneratesAndUsesSnapshotNotCurrentConversation() throws Exception {
		generate();
		jdbc.update("UPDATE portfolios SET description='수정된 결제 본문' WHERE id=1");
		jdbc.update("UPDATE ai_conversations SET current_conditions='{}'");
		mvc.perform(get(path()).header("Authorization", authorization)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("STALE"));
		doAnswer(call -> {
			assertTrue(((String) call.getArgument(0)).contains("결제 구현 경험"));
			return mapper.valueToTree(Map.of("assessments", List.of()));
		}).when(gemini).generate(anyString(), anyString(), any());
		assertEquals("UNKNOWN", mapper.readTree(generate()).path("assessments").get(0).path("status").asText());
	}
	@Test void noRelevantPublicBodyReturnsNoDataWithoutGeminiAndNeverFillsFive() throws Exception {
		jdbc.update("DELETE FROM portfolios");
		portfolio(2, "요리", "식단 기록", "PUBLIC", "요리 기록");
		jdbc.update("UPDATE portfolios SET role='요리사',tech_stack='[]'");
		portfolio(3, "결제", "Java", "PRIVATE", "비공개 원문");
		assertEquals("NO_DATA", mapper.readTree(generate()).path("status").asText());
		verifyNoInteractions(gemini);
	}
	@Test void selectsAtMostFiveRelatedMetadataAndNeverSendsPrivateOrIdentityOrAttachments() throws Exception {
		for (int i=2;i<=8;i++) portfolio(i, "결제 구현 사례" + i, "결제 구현 경험", "PUBLIC", "결제 멱등성을 구현했습니다");
		portfolio(99, "비공개 비밀", "결제", "PRIVATE", "절대 전송하면 안 됨");
		doAnswer(call -> {
			String raw = call.getArgument(0);
			assertFalse(raw.contains("절대 전송")); assertFalse(raw.contains("@test.local")); assertFalse(raw.contains("attachments"));
			assertEquals(5, mapper.readTree(raw).path("portfolios").size());
			return mapper.valueToTree(Map.of("assessments", List.of()));
		}).when(gemini).generate(anyString(), anyString(), any());
		var body = mapper.readTree(generate());
		assertEquals(5, body.path("portfolios").size());
		assertFalse(body.path("portfolios").get(0).path("selectionReason").asText().isBlank());
	}
	@ParameterizedTest @ValueSource(ints = {429, 502, 503, 504})
	void providerFailurePersistsFailedWithoutPartialDataAndExplicitRetryWorks(int statusValue) throws Exception {
		when(gemini.generate(anyString(), anyString(), any())).thenThrow(new ResponseStatusException(HttpStatus.valueOf(statusValue), "합성 외부 오류"));
		mvc.perform(post(path()).header("Authorization", authorization)).andExpect(status().is(statusValue));
		var stored = mapper.readTree(jdbc.queryForObject("SELECT detailed_reason FROM ai_recommendations WHERE id=?", String.class, recommendation));
		assertEquals("FAILED", stored.path("status").asText()); assertTrue(stored.path("response").isNull());
		success(); assertEquals("READY", mapper.readTree(generate()).path("status").asText());
	}
	@Test void forgedQuoteBecomesUnknownAndInventedPortfolioFailsClosed() throws Exception {
		when(gemini.generate(anyString(), anyString(), any())).thenReturn(mapper.valueToTree(Map.of("assessments", List.of(Map.of(
			"requirementId", "r0", "status", "MET", "portfolioId", 1, "field", "description", "quote", "없는 근거")))));
		assertEquals("UNKNOWN", mapper.readTree(generate()).path("assessments").get(0).path("status").asText());
		jdbc.update("UPDATE ai_recommendations SET detailed_reason=NULL");
		when(gemini.generate(anyString(), anyString(), any())).thenReturn(mapper.valueToTree(Map.of("assessments", List.of(Map.of(
			"requirementId", "r0", "status", "MET", "portfolioId", 999, "field", "description", "quote", "없는 근거")))));
		mvc.perform(post(path()).header("Authorization", authorization)).andExpect(status().isBadGateway());
	}
	@Test void concurrentRequestsAcrossServiceCallsHaveOneModelOwnerAndOtherRequestGetsPending() throws Exception {
		var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
		doAnswer(call -> {
			entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS));
			return mapper.valueToTree(Map.of("assessments", List.of()));
		}).when(gemini).generate(anyString(), anyString(), any());
		try (var executor = Executors.newSingleThreadExecutor()) {
			var task = executor.submit(() -> generate());
			try {
				assertTrue(entered.await(10, TimeUnit.SECONDS));
				mvc.perform(post(path()).header("Authorization", authorization)).andExpect(status().isAccepted())
					.andExpect(header().string("Retry-After", "3")).andExpect(jsonPath("$.assessments").isEmpty());
			} finally { release.countDown(); }
			assertEquals("READY", mapper.readTree(task.get(10, TimeUnit.SECONDS)).path("status").asText());
		}
		verify(gemini, times(1)).generate(anyString(), anyString(), any());
	}
	@Test void expiredLeaseCanBeRetriedAndOldTokenCannotOverwriteNewResult() throws Exception {
		String old = mapper.writeValueAsString(new AiPortfolioAnalysisService.Stored(1, "PENDING", "old-token", now().minusMinutes(1), "old", null, null));
		jdbc.update("UPDATE ai_recommendations SET detailed_reason=? WHERE id=?", old, recommendation);
		assertEquals("READY", mapper.readTree(generate()).path("status").asText());
		assertFalse(analysis.replace(recommendation, old, "stale writer"));
	}
	@Test void expiryOrDataChangeDuringModelCallDiscardsAnalysisAndKeepsOriginalScore() throws Exception {
		doAnswer(call -> {
			subscription("EXPIRED", now().minusDays(2), now().minusDays(1), null);
			return mapper.valueToTree(Map.of("assessments", List.of()));
		}).when(gemini).generate(anyString(), anyString(), any());
		mvc.perform(post(path()).header("Authorization", authorization)).andExpect(status().isForbidden());
		subscription("ACTIVE", now().minusDays(1), now().plusDays(1), null);
		doAnswer(call -> {
			jdbc.update("UPDATE portfolios SET visibility='PRIVATE'");
			return mapper.valueToTree(Map.of("assessments", List.of()));
		}).when(gemini).generate(anyString(), anyString(), any());
		mvc.perform(post(path()).header("Authorization", authorization)).andExpect(status().isConflict());
		assertEquals(new BigDecimal("80.00"), jdbc.queryForObject("SELECT match_score FROM ai_recommendations WHERE id=?", BigDecimal.class, recommendation));
	}
	@Test void malformedIdsBodiesAndUnavailableStoreDoNotExposeData() throws Exception {
		mvc.perform(post(path()).header("Authorization", authorization).contentType("application/json").content("{\"userId\":1}"))
			.andExpect(status().isBadRequest());
		mvc.perform(get(path().replace("/" + recommendation + "/", "/0/")).header("Authorization", authorization)).andExpect(status().isBadRequest());
		jdbc.execute("ALTER TABLE subscriptions RENAME TO subscriptions_hidden");
		try { mvc.perform(get(path()).header("Authorization", authorization)).andExpect(status().isServiceUnavailable()); }
		finally { jdbc.execute("ALTER TABLE subscriptions_hidden RENAME TO subscriptions"); }
		verifyNoInteractions(gemini);
	}
}
