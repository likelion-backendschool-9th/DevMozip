package com.devmozip.backend.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.event.ProjectApplicationChanged;
import com.devmozip.backend.event.ProjectLifecycleChanged;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * WBS 12.3 프로젝트 지원·상태·기간·종료 알림. 실제 프로젝트 API로 이벤트를 발행하고 이 테스트 전용 PostgreSQL에서 수신자와 문구를 확인한다.
 * 101은 주최자(PREMIUM)다.
 */
@SpringBootTest(properties = "project.current-user.development-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationProjectEventIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper mapper;
	@Autowired JwtProvider jwt;
	@Autowired ApplicationEventPublisher publisher;
	@Autowired PlatformTransactionManager transactionManager;
	@MockitoBean RefreshTokenStore refresh;
	@MockitoBean ProjectPortfolioAccess portfolios;
	@MockitoBean(name = "projectClock") Clock clock;

	static final String TITLE = "알림 검증 프로젝트";

	@BeforeEach
	void reset() {
		for (String table : List.of("notifications", "project_application_portfolio_snapshots", "project_application_portfolios",
			"project_applications", "review_keyword_selections", "reviews", "project_members", "projects", "users")) {
			jdbc.update("DELETE FROM " + table);
		}
		Map<Long, String> names = Map.of(101L, "주최자", 202L, "지원자A", 303L, "지원자B", 404L, "지원자C", 505L, "지원자D");
		names.forEach((id, nickname) -> jdbc.update("INSERT INTO users(id, email, nickname, role, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
			id, id + "@notice.test", nickname, id == 101 ? "PREMIUM" : "USER"));
		when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
		when(clock.instant()).thenReturn(LocalDateTime.parse("2026-11-06T12:00:00").atZone(ZoneId.of("Asia/Seoul")).toInstant());
		when(portfolios.available()).thenReturn(true);
		when(portfolios.ownedBy(anyLong())).thenAnswer(i -> List.of(new ProjectPortfolioAccess.Portfolio(i.getArgument(0, Long.class) * 10, "본인 포트폴리오")));
		when(portfolios.ownsAll(anyLong(), anyList())).thenAnswer(i -> {
			long user = i.getArgument(0);
			List<Long> ids = i.getArgument(1);
			return ids.stream().allMatch(id -> id == user * 10);
		});
	}

	String token(long id) {
		return "Bearer " + jwt.createAccessToken(id + "@notice.test", id == 101 ? "PREMIUM" : "USER");
	}

	String base(long id) {
		return "/api/v1/projects/" + id;
	}

	long project(int capacity) throws Exception {
		var body = new LinkedHashMap<String, Object>(Map.of("title", TITLE, "description", "알림 이벤트를 검증합니다.", "imageUrl", "https://example.test/a.png",
			"isPublic", true, "recruitmentSlots", List.of(Map.of("roleCode", "SRE", "capacity", capacity, "minimumYears", 0)),
			"techStacks", List.of("Java"), "interests", List.of("교육"), "recruitmentStart", "2026-11-01", "recruitmentEnd", "2026-11-20",
			"developmentStart", "2026-11-06"));
		body.put("developmentEnd", "2026-12-30");
		var response = mvc.perform(post("/api/v1/projects").header("Authorization", token(101)).contentType("application/json")
			.content(mapper.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse();
		return mapper.readTree(response.getContentAsString()).get("id").asLong();
	}

	long apply(long id, long user) throws Exception {
		var response = mvc.perform(post(base(id) + "/applications").header("Authorization", token(user)).contentType("application/json")
			.content(mapper.writeValueAsString(Map.of("jobRole", "SRE", "portfolioIds", List.of(user * 10), "introduction", "함께 기여하고 싶습니다."))))
			.andExpect(status().isCreated()).andReturn().getResponse();
		return mapper.readTree(response.getContentAsString()).get("id").asLong();
	}

	ResultActions decide(long id, long application, long user, String body) throws Exception {
		return mvc.perform(patch(base(id) + "/applications/" + application).header("Authorization", token(user))
			.contentType("application/json").content(body));
	}

	ResultActions lifecycle(long id, long user, String body) throws Exception {
		return mvc.perform(patch(base(id) + "/update").header("Authorization", token(user)).contentType("application/json").content(body));
	}

	void member(long project, long user) {
		jdbc.update("INSERT INTO project_members(project_id, user_id, role, job_role, status, joined_at, created_at, updated_at)"
			+ " VALUES (?, ?, 'PARTICIPANT', 'SRE', 'ACTIVE', localtimestamp, localtimestamp, localtimestamp)", project, user);
	}

	/** 수신자별 "종류|본문" 목록(생성 순) */
	List<String> notices(long recipient) {
		return jdbc.queryForList("SELECT type || '|' || body FROM notifications WHERE recipient_id = ? ORDER BY id", String.class, recipient);
	}

	List<Long> recipients(String type) {
		return jdbc.queryForList("SELECT recipient_id FROM notifications WHERE type = ? ORDER BY recipient_id", Long.class, type);
	}

	@Test
	void applicationChangesNotifyHostOrApplicant() throws Exception {
		long id = project(3);
		long a = apply(id, 202), b = apply(id, 303), c = apply(id, 404);
		decide(id, a, 101, "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
		decide(id, b, 101, "{\"status\":\"REJECTED\",\"rejectionReason\":\"요구 경험과 맞지 않습니다.\"}").andExpect(status().isOk());
		decide(id, c, 404, "{\"status\":\"CANCELLED\"}").andExpect(status().isOk());

		assertEquals(List.of("PROJECT_APPLICATION_SUBMITTED|지원자A님이 지원했습니다.", "PROJECT_APPLICATION_SUBMITTED|지원자B님이 지원했습니다.",
			"PROJECT_APPLICATION_SUBMITTED|지원자C님이 지원했습니다.", "PROJECT_APPLICATION_CANCELLED|지원자C님이 지원을 취소했습니다."), notices(101));
		assertEquals(List.of("PROJECT_APPLICATION_APPROVED|" + TITLE + ": 팀원으로 등록되었습니다."), notices(202));
		assertEquals(List.of("PROJECT_APPLICATION_REJECTED|" + TITLE + ": 지원이 거절되었습니다."), notices(303));
		assertEquals(List.of(), notices(404));

		mvc.perform(get("/api/v1/notifications").header("Authorization", token(101))).andExpect(status().isOk())
			.andExpect(jsonPath("$.unreadCount").value(4))
			.andExpect(jsonPath("$.items[0].type").value("PROJECT_APPLICATION_CANCELLED"))
			.andExpect(jsonPath("$.items[0].title").value("프로젝트 지원 취소"))
			.andExpect(jsonPath("$.items[0].actionUrl").value("/projects/" + id))
			.andExpect(jsonPath("$.items[0].projectId").value(id))
			.andExpect(jsonPath("$.items[0].review").isEmpty());
		mvc.perform(get("/api/v1/notifications").header("Authorization", token(202)))
			.andExpect(jsonPath("$.items[0].title").value("지원 승인")).andExpect(jsonPath("$.unreadCount").value(1));
	}

	@Test
	void statusAndPeriodChangesGoToActiveMembersAndPendingApplicantsExceptActor() throws Exception {
		long id = project(3);
		long active = apply(id, 202), left = apply(id, 303), pending = apply(id, 404), rejected = apply(id, 505);
		decide(id, active, 101, "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
		decide(id, left, 101, "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
		decide(id, left, 303, "{\"status\":\"CANCELLED\"}").andExpect(status().isOk());
		decide(id, rejected, 101, "{\"status\":\"REJECTED\",\"rejectionReason\":\"모집 직군과 맞지 않습니다.\"}").andExpect(status().isOk());
		assertTrue(pending > 0);
		jdbc.update("DELETE FROM notifications");

		lifecycle(id, 101, "{\"action\":\"EXTEND_PERIOD\",\"developmentEnd\":\"2027-01-10\"}").andExpect(status().isOk());
		assertEquals(List.of(202L, 404L), recipients("PROJECT_PERIOD_EXTENDED"));
		assertEquals(List.of("PROJECT_PERIOD_EXTENDED|" + TITLE + ": 변경된 모집 조건과 기간을 확인해 주세요."), notices(202));

		lifecycle(id, 101, "{\"action\":\"CHANGE_STATUS\",\"status\":\"RECRUITMENT_COMPLETED\"}").andExpect(status().isOk());
		assertEquals(List.of(202L, 404L), recipients("PROJECT_STATUS_CHANGED"));
		assertEquals("PROJECT_STATUS_CHANGED|" + TITLE + ": 모집 완료", notices(404).getLast());
		assertEquals(List.of(), notices(101));
		assertEquals(List.of(), notices(303));
		assertEquals(List.of(), notices(505));
	}

	@Test
	void completionByEndRequestMajorityNotifiesActiveMembersExceptActor() throws Exception {
		long id = project(3);
		member(id, 202);
		member(id, 303);
		jdbc.update("UPDATE projects SET status = 'IN_PROGRESS' WHERE id = ?", id);
		lifecycle(id, 101, "{\"action\":\"REQUEST_END\"}").andExpect(status().isOk());
		assertEquals(0, recipients("PROJECT_COMPLETED").size());
		lifecycle(id, 202, "{\"action\":\"REQUEST_END\"}").andExpect(status().isOk());
		assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM projects WHERE id = ?", String.class, id));
		assertEquals(List.of(101L, 303L), recipients("PROJECT_COMPLETED"));
		assertTrue(notices(303).contains("PROJECT_COMPLETED|" + TITLE + " 프로젝트가 종료되었습니다."));
	}

	@Test
	void completionWhenApprovedMemberLeavesNotifiesRemainingMembers() throws Exception {
		long id = project(3);
		long a = apply(id, 202), b = apply(id, 303), c = apply(id, 404);
		for (long application : new long[] {a, b, c}) decide(id, application, 101, "{\"status\":\"APPROVED\"}").andExpect(status().isOk());
		jdbc.update("UPDATE projects SET status = 'IN_PROGRESS' WHERE id = ?", id);
		jdbc.update("DELETE FROM notifications");
		lifecycle(id, 101, "{\"action\":\"REQUEST_END\"}").andExpect(status().isOk());
		lifecycle(id, 202, "{\"action\":\"REQUEST_END\"}").andExpect(status().isOk());
		assertEquals(0, recipients("PROJECT_COMPLETED").size());

		decide(id, c, 404, "{\"status\":\"CANCELLED\"}").andExpect(status().isOk());
		assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM projects WHERE id = ?", String.class, id));
		assertEquals(List.of(101L, 202L, 303L), recipients("PROJECT_COMPLETED"));
		assertEquals(List.of(101L), recipients("PROJECT_APPLICATION_CANCELLED"));
	}

	@Test
	void rolledBackEventCreatesNothingAndSameEventCreatesOne() throws Exception {
		long id = project(2);
		var event = new ProjectApplicationChanged(ProjectApplicationChanged.Type.SUBMITTED, id, 9_999, 202, 101, "SRE", false,
			"project-application:9999:SUBMITTED", LocalDateTime.parse("2026-11-06T12:00:00"));
		var tx = new TransactionTemplate(transactionManager);
		tx.executeWithoutResult(status -> {
			publisher.publishEvent(event);
			status.setRollbackOnly();
		});
		assertEquals(List.of(), notices(101));

		tx.executeWithoutResult(status -> publisher.publishEvent(event));
		tx.executeWithoutResult(status -> publisher.publishEvent(event));
		assertEquals(List.of("PROJECT_APPLICATION_SUBMITTED|지원자A님이 지원했습니다."), notices(101));

		member(id, 202);
		var changed = new ProjectLifecycleChanged(ProjectLifecycleChanged.Type.STATUS_CHANGED, id, 101, ProjectStatus.RECRUITING,
			ProjectStatus.RECRUITMENT_COMPLETED, null, null, null, null, LocalDateTime.parse("2026-11-06T12:00:00"));
		tx.executeWithoutResult(status -> publisher.publishEvent(changed));
		tx.executeWithoutResult(status -> publisher.publishEvent(changed));
		assertEquals(List.of(202L), recipients("PROJECT_STATUS_CHANGED"));
	}

	@Test
	void notificationFailureDoesNotChangeOriginalRequest() throws Exception {
		long id = project(2);
		jdbc.execute("ALTER TABLE notifications ADD CONSTRAINT notifications_block_test CHECK (false) NOT VALID");
		try {
			long application = apply(id, 202);
			assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM project_applications WHERE id = ?", String.class, application));
			decide(id, application, 101, "{\"status\":\"APPROVED\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"));
			assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class));
		} finally {
			jdbc.execute("ALTER TABLE notifications DROP CONSTRAINT notifications_block_test");
		}
	}
}
