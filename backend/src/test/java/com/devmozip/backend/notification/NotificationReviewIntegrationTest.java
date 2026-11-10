package com.devmozip.backend.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.devmozip.backend.event.ProjectReviewNotificationEvent;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.service.ProjectPortfolioAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * WBS 12.4 리뷰 요청·재알림 알림과 작성 완료·기간 만료 계산(D4 A). 실제 프로젝트 종료·리뷰 API로 확인한다.
 * 101(주최자)·202·303이 팀원이며, 202의 종료 동의로 과반이 되어 종료된다. 리뷰 만료는 종료 30일 뒤 2026-12-06 12:00이다.
 */
@RecordApplicationEvents
@SpringBootTest(properties = "project.current-user.development-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationReviewIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper mapper;
	@Autowired JwtProvider jwt;
	@Autowired ApplicationEvents events;
	@Autowired ApplicationEventPublisher publisher;
	@Autowired PlatformTransactionManager transactionManager;
	@MockitoBean RefreshTokenStore refresh;
	@MockitoBean ProjectPortfolioAccess portfolios;
	@MockitoBean(name = "projectClock") Clock clock;

	static final String TITLE = "리뷰 알림 프로젝트";
	static final String REQUEST_BODY = TITLE + ": 2026-12-06 12:00까지 본인을 제외한 팀원을 평가해 주세요.";

	@BeforeEach
	void reset() {
		for (String table : List.of("notifications", "project_application_portfolio_snapshots", "project_application_portfolios",
			"project_applications", "review_keyword_selections", "reviews", "review_keywords", "project_members", "projects", "users")) {
			jdbc.update("DELETE FROM " + table);
		}
		Map<Long, String> names = Map.of(101L, "주최자", 202L, "팀원A", 303L, "팀원B", 404L, "외부회원");
		names.forEach((id, nickname) -> jdbc.update("INSERT INTO users(id, email, nickname, role, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
			id, id + "@review-notice.test", nickname, id == 101 ? "PREMIUM" : "USER"));
		jdbc.update("INSERT INTO review_keywords(name, is_active, created_at, updated_at) VALUES ('소통', true, localtimestamp, localtimestamp)");
		when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
		date("2026-11-06T12:00:00");
		when(portfolios.available()).thenReturn(true);
		when(portfolios.ownedBy(anyLong())).thenAnswer(i -> List.of(new ProjectPortfolioAccess.Portfolio(i.getArgument(0, Long.class) * 10, "본인 포트폴리오")));
	}

	void date(String text) {
		when(clock.instant()).thenReturn(LocalDateTime.parse(text).atZone(ZoneId.of("Asia/Seoul")).toInstant());
	}

	String token(long id) {
		return "Bearer " + jwt.createAccessToken(id + "@review-notice.test", id == 101 ? "PREMIUM" : "USER");
	}

	String base(long id) {
		return "/api/v1/projects/" + id;
	}

	long ended() throws Exception {
		var body = new LinkedHashMap<String, Object>(Map.of("title", TITLE, "description", "리뷰 알림을 검증합니다.", "imageUrl", "https://example.test/a.png",
			"isPublic", true, "recruitmentSlots", List.of(Map.of("roleCode", "SRE", "capacity", 2, "minimumYears", 0)),
			"techStacks", List.of("Java"), "interests", List.of("교육"), "recruitmentStart", "2026-11-01", "recruitmentEnd", "2026-11-20",
			"developmentStart", "2026-11-06"));
		body.put("developmentEnd", "2026-12-30");
		var response = mvc.perform(post("/api/v1/projects").header("Authorization", token(101)).contentType("application/json")
			.content(mapper.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse();
		long id = mapper.readTree(response.getContentAsString()).get("id").asLong();
		for (long user : new long[] {202, 303}) {
			jdbc.update("INSERT INTO project_members(project_id, user_id, role, job_role, status, joined_at, created_at, updated_at)"
				+ " VALUES (?, ?, 'PARTICIPANT', 'SRE', 'ACTIVE', localtimestamp, localtimestamp, localtimestamp)", id, user);
		}
		jdbc.update("UPDATE projects SET status = 'IN_PROGRESS' WHERE id = ?", id);
		for (long user : new long[] {101, 202}) {
			mvc.perform(patch(base(id) + "/update").header("Authorization", token(user)).contentType("application/json")
				.content("{\"action\":\"REQUEST_END\"}")).andExpect(status().isOk());
		}
		return id;
	}

	JsonNode notices(long user, String status) throws Exception {
		var request = get("/api/v1/notifications").header("Authorization", token(user));
		if (status != null) request.param("status", status);
		return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	JsonNode reviewNotice(long user) throws Exception {
		for (JsonNode item : notices(user, null).get("items")) if (item.get("type").asText().equals("PROJECT_REVIEW_REQUESTED")) return item;
		throw new AssertionError("리뷰 요청 알림이 없습니다.");
	}

	List<Long> recipients(String type) {
		return jdbc.queryForList("SELECT recipient_id FROM notifications WHERE type = ? ORDER BY recipient_id", Long.class, type);
	}

	String link(long id, long user) throws Exception {
		var body = mapper.readTree(mvc.perform(post(base(id) + "/reviews/me/link").header("Authorization", token(user)))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		return body.get("href").asText().split("token=")[1];
	}

	ResultActions submitAll(long id, long user) throws Exception {
		String token = link(id, user);
		var targets = mapper.readTree(mvc.perform(get(base(id) + "/reviews/me").header("Authorization", token(user)))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("targets");
		var entries = new ArrayList<Map<String, Object>>();
		for (var target : targets) entries.add(Map.of("targetMemberId", target.get("memberId").asLong(), "rating", 5, "keywords", List.of("소통")));
		return mvc.perform(put(base(id) + "/reviews/me").header("Authorization", token(user)).contentType("application/json")
			.content(mapper.writeValueAsString(Map.of("token", token, "entries", entries))));
	}

	@Test
	void completionCreatesOneReviewRequestPerReviewerWithoutTheTemporaryToken() throws Exception {
		long id = ended();
		assertEquals(List.of(101L, 202L, 303L), recipients("PROJECT_REVIEW_REQUESTED"));
		assertEquals(List.of(101L, 303L), recipients("PROJECT_COMPLETED"));
		assertEquals(List.of(REQUEST_BODY), jdbc.queryForList(
			"SELECT DISTINCT body FROM notifications WHERE type = 'PROJECT_REVIEW_REQUESTED'", String.class));
		assertEquals(List.of("/projects/" + id + "/reviews"), jdbc.queryForList(
			"SELECT DISTINCT action_url FROM notifications WHERE type = 'PROJECT_REVIEW_REQUESTED'", String.class));
		assertEquals(0, jdbc.queryForObject("""
			SELECT count(*) FROM notifications n WHERE n.type = 'PROJECT_REVIEW_REQUESTED' AND (n.project_id <> ? OR n.review_id IS DISTINCT FROM
			  (SELECT min(r.id) FROM reviews r JOIN project_members m ON m.id = r.reviewer_member_id WHERE r.project_id = ? AND m.user_id = n.recipient_id))
			""", Integer.class, id, id));

		var hrefs = events.stream(ProjectReviewNotificationEvent.class).map(ProjectReviewNotificationEvent::href).toList();
		assertEquals(3, hrefs.size());
		for (String href : hrefs) {
			String secret = href.split("token=")[1];
			assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE token LIKE ? OR body LIKE ? OR action_url LIKE ? OR title LIKE ?",
				Integer.class, "%" + secret + "%", "%" + secret + "%", "%" + secret + "%", "%" + secret + "%"));
		}
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM messages WHERE content LIKE '%리뷰%'", Integer.class));

		var item = reviewNotice(202);
		assertEquals("협업 리뷰 작성 요청", item.get("title").asText());
		assertEquals("PENDING", item.get("review").get("status").asText());
		assertEquals("2026-12-06T12:00:00", item.get("review").get("expiresAt").asText());
		assertFalse(item.get("resolved").asBoolean());
		assertFalse(item.get("read").asBoolean());
		assertEquals(1, notices(202, null).get("unreadCount").asLong());
		assertEquals(2, notices(101, null).get("unreadCount").asLong());
		assertEquals(0, notices(404, null).get("items").size());
	}

	@Test
	void readStateAndReviewCompletionDoNotChangeEachOther() throws Exception {
		long id = ended();
		long notice = reviewNotice(202).get("id").asLong();
		String reviewsOf202 = "SELECT r.status || r.token_hash FROM reviews r JOIN project_members m ON m.id = r.reviewer_member_id"
			+ " WHERE m.user_id = 202 ORDER BY r.id";
		List<String> before = jdbc.queryForList(reviewsOf202, String.class);
		mvc.perform(patch("/api/v1/notifications/" + notice + "/read").header("Authorization", token(202)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true)).andExpect(jsonPath("$.unreadCount").value(0));
		assertEquals(before, jdbc.queryForList(reviewsOf202, String.class));
		assertEquals("PENDING", reviewNotice(202).get("review").get("status").asText());
		mvc.perform(patch("/api/v1/notifications/" + notice + "/read").header("Authorization", token(202))
			.contentType("application/json").content("{\"read\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1));

		submitAll(id, 202).andExpect(status().isOk()).andExpect(jsonPath("$.submitted").value(true));
		var item = reviewNotice(202);
		assertEquals("COMPLETED", item.get("review").get("status").asText());
		assertTrue(item.get("resolved").asBoolean());
		assertFalse(item.get("read").asBoolean());
		assertNull(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", LocalDateTime.class, notice));
		assertEquals(0, notices(202, null).get("unreadCount").asLong());
		assertEquals(0, notices(202, "unread").get("items").size());
		assertEquals(notice, notices(202, "read").get("items").get(0).get("id").asLong());
		assertEquals("PENDING", reviewNotice(303).get("review").get("status").asText());
	}

	@Test
	void expiryBoundaryFollowsReviewExpiresAt() throws Exception {
		ended();
		date("2026-12-06T11:59:59");
		assertEquals("PENDING", reviewNotice(303).get("review").get("status").asText());
		assertEquals(2, notices(303, null).get("unreadCount").asLong());
		date("2026-12-06T12:00:00");
		var item = reviewNotice(303);
		assertEquals("EXPIRED", item.get("review").get("status").asText());
		assertTrue(item.get("resolved").asBoolean());
		assertEquals(1, notices(303, null).get("unreadCount").asLong());
		assertEquals(1, notices(303, "unread").get("items").size());
		assertEquals("PROJECT_COMPLETED", notices(303, "unread").get("items").get(0).get("type").asText());
	}

	@Test
	void reminderGoesToReviewersWithPendingReviewsOnce() throws Exception {
		long id = ended();
		submitAll(id, 202).andExpect(status().isOk());
		date("2026-11-09T12:00:00");
		mvc.perform(post(base(id) + "/reviews/reminders").header("Authorization", token(101)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.requested").value(2));
		assertEquals(List.of(101L, 303L), recipients("PROJECT_REVIEW_REMINDED"));
		assertEquals(List.of(TITLE + ": 아직 평가하지 않은 팀원이 있습니다."), jdbc.queryForList(
			"SELECT DISTINCT body FROM notifications WHERE type = 'PROJECT_REVIEW_REMINDED'", String.class));
		assertEquals(0, jdbc.queryForObject("""
			SELECT count(*) FROM notifications reminded JOIN notifications requested ON requested.recipient_id = reminded.recipient_id
			WHERE reminded.type = 'PROJECT_REVIEW_REMINDED' AND requested.type = 'PROJECT_REVIEW_REQUESTED'
			  AND (reminded.review_id <> requested.review_id OR reminded.action_url <> requested.action_url)
			""", Integer.class));
		assertEquals("PENDING", notices(303, null).get("items").get(0).get("review").get("status").asText());

		var reminder = events.stream(ProjectReviewNotificationEvent.class).filter(ProjectReviewNotificationEvent::reminder).findFirst().orElseThrow();
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> publisher.publishEvent(reminder));
		assertEquals(2, recipients("PROJECT_REVIEW_REMINDED").size());
	}
}
