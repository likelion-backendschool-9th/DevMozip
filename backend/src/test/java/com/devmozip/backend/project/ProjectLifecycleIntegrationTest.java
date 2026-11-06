package com.devmozip.backend.project;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.entity.enums.ProjectJobRole;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.event.ProjectLifecycleChanged;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import com.devmozip.backend.service.ProjectService;
import com.devmozip.backend.service.ProjectLifecycleService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.devmozip.backend.dto.project.ProjectLifecycleRequest.Action.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Sql("classpath:project/lifecycle-applications.sql")
@Import(ProjectLifecycleIntegrationTest.EventsConfiguration.class)
class ProjectLifecycleIntegrationTest {
	@MockitoBean com.devmozip.backend.service.ProjectCreationPolicy creationPolicy;
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ProjectService projects;
	@Autowired ProjectLifecycleService lifecycle;
	@Autowired ObjectMapper mapper;
	@Autowired JwtProvider jwt;
	@Autowired PlatformTransactionManager transactions;
	@Autowired CommittedEvents events;
	@MockitoBean RefreshTokenStore refreshTokens;
	@MockitoBean ProjectCurrentUserProvider user;
	@MockitoBean(name="projectClock") Clock clock;
	private final ThreadLocal<Long> actor = ThreadLocal.withInitial(() -> 101L);
	private final LocalDate today = LocalDate.of(2026,11,5);

	@BeforeEach void reset() {
		jdbc.update("DELETE FROM project_applications"); jdbc.update("DELETE FROM project_members"); jdbc.update("DELETE FROM projects");
		actor.set(101L); when(user.getCurrentUserId()).thenAnswer(invocation -> actor.get());
		when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
		when(clock.instant()).thenReturn(Instant.parse("2026-11-04T15:30:00Z"));
		events.values.clear();
	}
	private long fixture() {
		return projects.create(new CreateProjectRequest("Lifecycle", "요약", "상태와 기간 테스트", null, "https://example.test/a.png", true,
			List.of(new CreateProjectRequest.RecruitmentSlot(ProjectJobRole.BACKEND, 5, 0)),List.of("Java"),List.of("교육"),
			today.minusDays(1),today.plusDays(7),today,today.plusDays(90))).id();
	}
	private ProjectLifecycleRequest command(ProjectLifecycleRequest.Action action) { return new ProjectLifecycleRequest(action,null,null,null); }
	private ProjectLifecycleRequest statusCommand(ProjectStatus status) { return new ProjectLifecycleRequest(CHANGE_STATUS,status,null,null); }
	private ProjectLifecycleRequest extend(LocalDate rec, LocalDate dev) { return new ProjectLifecycleRequest(EXTEND_PERIOD,null,rec,dev); }
	private void member(long project, long id, String status) {
		jdbc.update("INSERT INTO project_members(project_id,user_id,role,job_role,status,joined_at,created_at,updated_at) VALUES (?,?,'PARTICIPANT','BACKEND',?,localtimestamp,localtimestamp,localtimestamp)",project,id,status);
	}
	private void inProgress(long id) { jdbc.update("UPDATE projects SET status='IN_PROGRESS' WHERE id=?",id); }
	private void rejected(int code, Runnable operation) {
		assertEquals(code,assertThrows(org.springframework.web.server.ResponseStatusException.class,operation::run).getStatusCode().value());
	}
	private org.springframework.test.web.servlet.ResultActions patchCommand(long id, Object body, boolean authenticated) throws Exception {
		var request = patch("/api/v1/projects/"+id+"/update").contentType("application/json").content(mapper.writeValueAsString(body));
		if (authenticated) request.header("Authorization","Bearer "+jwt.createAccessToken("lifecycle-test","USER"));
		return mvc.perform(request);
	}
	@Test void endpointAuthenticatesAndReturnsRecordWithoutAcceptingBodyUserId() throws Exception {
		long id=fixture();
		patchCommand(id,Map.of("action","CHANGE_STATUS","status","RECRUITMENT_COMPLETED"),false).andExpect(status().isUnauthorized());
		patchCommand(id,Map.of("action","CHANGE_STATUS","status","RECRUITMENT_COMPLETED","userId",999),true)
			.andExpect(status().isOk()).andExpect(jsonPath("$.project.status").value("RECRUITMENT_COMPLETED"))
			.andExpect(jsonPath("$.project.hostMemberId").value(101)).andExpect(jsonPath("$.activeMemberCount").value(1));
	}
	@Test void rejectsMissingMixedAndUnknownCommands() throws Exception {
		long id=fixture();
		for (var body : List.of(Map.of(),Map.of("action","UNKNOWN"),Map.of("action","CHANGE_STATUS"),
			Map.of("action","REQUEST_END","status","COMPLETED"),Map.of("action","EXTEND_PERIOD"),
			Map.of("action","EXTEND_PERIOD","developmentEnd","2027-01-01","status","IN_PROGRESS")))
			patchCommand(id,body,true).andExpect(status().isBadRequest());
	}
	@Test void controlsTransitionsAndPreventsDirectCompletion() {
		long id=fixture();
		rejected(409,()->lifecycle.update(id,statusCommand(ProjectStatus.IN_PROGRESS)));
		rejected(409,()->lifecycle.update(id,statusCommand(ProjectStatus.COMPLETED)));
		assertEquals(ProjectStatus.RECRUITMENT_COMPLETED,lifecycle.update(id,statusCommand(ProjectStatus.RECRUITMENT_COMPLETED)).project().status());
		assertEquals(ProjectStatus.RECRUITING,lifecycle.update(id,statusCommand(ProjectStatus.RECRUITING)).project().status());
		lifecycle.update(id,statusCommand(ProjectStatus.RECRUITMENT_COMPLETED));
		assertEquals(ProjectStatus.IN_PROGRESS,lifecycle.update(id,statusCommand(ProjectStatus.IN_PROGRESS)).project().status());
		rejected(409,()->lifecycle.update(id,statusCommand(ProjectStatus.RECRUITMENT_COMPLETED)));
		rejected(409,()->lifecycle.update(id,statusCommand(ProjectStatus.COMPLETED)));
	}
	@Test void reopeningIncludesKstDeadlineDayButRejectsNextDay() {
		long id=fixture(); jdbc.update("UPDATE projects SET recruitment_end=? WHERE id=?",today,id);
		lifecycle.update(id,statusCommand(ProjectStatus.RECRUITMENT_COMPLETED));
		lifecycle.update(id,statusCommand(ProjectStatus.RECRUITING)); lifecycle.update(id,statusCommand(ProjectStatus.RECRUITMENT_COMPLETED));
		when(clock.instant()).thenReturn(Instant.parse("2026-11-05T15:00:00Z"));
		rejected(409,()->lifecycle.update(id,statusCommand(ProjectStatus.RECRUITING)));
	}
	@Test void pendingApplicationsBlockStartUntilProcessed() {
		long id=fixture(); lifecycle.update(id,statusCommand(ProjectStatus.RECRUITMENT_COMPLETED));
		jdbc.update("INSERT INTO project_applications(project_id,applicant_member_id,job_role,status,applied_at,created_at,updated_at) VALUES (?,202,'BACKEND','PENDING',localtimestamp,localtimestamp,localtimestamp)",id);
		rejected(409,()->lifecycle.update(id,statusCommand(ProjectStatus.IN_PROGRESS)));
		jdbc.update("UPDATE project_applications SET status='REJECTED' WHERE project_id=?",id);
		assertEquals(ProjectStatus.IN_PROGRESS,lifecycle.update(id,statusCommand(ProjectStatus.IN_PROGRESS)).project().status());
	}
	@Test void onlyActiveHostChangesStatusAndPeriod() {
		long id=fixture(); member(id,202,"ACTIVE"); actor.set(202L);
		rejected(403,()->lifecycle.update(id,statusCommand(ProjectStatus.RECRUITMENT_COMPLETED)));
		rejected(403,()->lifecycle.update(id,extend(null,today.plusDays(120))));
		actor.set(101L); jdbc.update("UPDATE project_members SET status='LEFT' WHERE project_id=? AND user_id=101",id);
		rejected(403,()->lifecycle.update(id,statusCommand(ProjectStatus.RECRUITMENT_COMPLETED)));
	}
	@Test void extendsOneOrBothEndDatesPreservingStartAndPublishesOldDates() {
		long id=fixture();
		var response=lifecycle.update(id,extend(null,today.plusDays(120)));
		assertEquals(today.plusDays(7),response.project().recruitmentEnd()); assertEquals(today,response.project().developmentStart());
		lifecycle.update(id,extend(today.plusDays(14),today.plusDays(150)));
		assertEquals(2,events.values.size());
		var event=events.values.peek(); assertEquals(ProjectLifecycleChanged.Type.PERIOD_EXTENDED,event.type());
		assertEquals(today.plusDays(90),event.previousDevelopmentEnd()); assertEquals(today.plusDays(120),event.developmentEnd());
		rejected(409,()->lifecycle.update(id,extend(today.plusDays(13),null)));
		rejected(409,()->lifecycle.update(id,extend(null,today.plusDays(150))));
		rejected(400,()->lifecycle.update(id,extend(today.plusDays(151),null)));
	}
	@ParameterizedTest @ValueSource(ints={4,5})
	void moreThanHalfOfActiveTeamIncludingHostCompletesExactlyOnce(int count) {
		long id=fixture(); for(int n=1;n<count;n++) member(id,101+n,"ACTIVE"); inProgress(id);
		for(int n=0;n<count/2;n++) { actor.set(101L+n); assertEquals(ProjectStatus.IN_PROGRESS,lifecycle.update(id,command(REQUEST_END)).project().status()); }
		actor.set(101L+count/2); var completed=lifecycle.update(id,command(REQUEST_END));
		assertEquals(ProjectStatus.COMPLETED,completed.project().status()); assertEquals(count/2+1,completed.endRequestCount());
		assertNotNull(jdbc.queryForObject("SELECT completed_at FROM projects WHERE id=?",java.sql.Timestamp.class,id));
		assertEquals(1,events.values.stream().filter(e->e.type()==ProjectLifecycleChanged.Type.COMPLETED).count());
		rejected(409,()->lifecycle.update(id,command(REQUEST_END))); rejected(409,()->lifecycle.update(id,command(CANCEL_END_REQUEST)));
		rejected(409,()->lifecycle.update(id,extend(null,today.plusDays(120))));
	}
	@Test void endRequestsOnlyWhileInProgressAndCanBeCancelledBeforeCompletion() {
		long id=fixture(); member(id,102,"ACTIVE"); member(id,103,"ACTIVE");
		rejected(409,()->lifecycle.update(id,command(REQUEST_END))); inProgress(id);
		lifecycle.update(id,command(REQUEST_END)); rejected(409,()->lifecycle.update(id,command(REQUEST_END)));
		assertEquals(0,lifecycle.update(id,command(CANCEL_END_REQUEST)).endRequestCount());
		rejected(409,()->lifecycle.update(id,command(CANCEL_END_REQUEST)));
		assertEquals(1,lifecycle.update(id,command(REQUEST_END)).endRequestCount());
	}
	@Test void ignoresLeftMembersAndTheirRequestsAndUsesCurrentTeamSize() {
		long id=fixture(); for(long n=102;n<=105;n++)member(id,n,"ACTIVE"); inProgress(id);
		jdbc.update("UPDATE project_members SET status='LEFT',end_requested_at=localtimestamp WHERE project_id=? AND user_id=105",id);
		actor.set(105L); rejected(403,()->lifecycle.update(id,command(REQUEST_END)));
		actor.set(101L); assertEquals(4,lifecycle.update(id,command(REQUEST_END)).activeMemberCount());
		actor.set(102L); assertEquals(ProjectStatus.IN_PROGRESS,lifecycle.update(id,command(REQUEST_END)).project().status());
		actor.set(103L); assertEquals(ProjectStatus.COMPLETED,lifecycle.update(id,command(REQUEST_END)).project().status());
	}
	@Test void addedMembersIncreaseRequiredVotes() {
		long id=fixture(); member(id,102,"ACTIVE"); member(id,103,"ACTIVE"); inProgress(id);
		lifecycle.update(id,command(REQUEST_END)); member(id,104,"ACTIVE");member(id,105,"ACTIVE");
		actor.set(102L); var pending=lifecycle.update(id,command(REQUEST_END));
		assertEquals(3,pending.requiredEndRequestCount());assertEquals(ProjectStatus.IN_PROGRESS,pending.project().status());
		actor.set(103L); assertEquals(ProjectStatus.COMPLETED,lifecycle.update(id,command(REQUEST_END)).project().status());
	}
	@Test void concurrentRequestsSerializeAndEmitOnlyOneCompletion() throws Exception {
		long id=fixture();member(id,102,"ACTIVE");member(id,103,"ACTIVE");inProgress(id);
		var executor=Executors.newFixedThreadPool(3); var start=new CountDownLatch(1);
		try {
			List<Future<Integer>> futures=new ArrayList<>();
			for(long n=101;n<=103;n++){long who=n;futures.add(executor.submit(()->{actor.set(who);start.await();try{lifecycle.update(id,command(REQUEST_END));return 200;}catch(org.springframework.web.server.ResponseStatusException ex){return ex.getStatusCode().value();}finally{actor.remove();}}));}
			start.countDown(); int successes=0;
			for(var f:futures){int code=f.get(15,TimeUnit.SECONDS);assertTrue(code==200||code==409);if(code==200)successes++;}
			assertEquals(2,successes);assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM projects WHERE id=?",String.class,id));
			assertEquals(1,events.values.stream().filter(e->e.type()==ProjectLifecycleChanged.Type.COMPLETED).count());
		}finally{executor.shutdownNow();}
	}
	@Test void rolledBackChangesDoNotDeliverCommittedEvents() {
		long id=fixture();
		new TransactionTemplate(transactions).executeWithoutResult(tx->{lifecycle.update(id,statusCommand(ProjectStatus.RECRUITMENT_COMPLETED));tx.setRollbackOnly();});
		assertEquals("RECRUITING",jdbc.queryForObject("SELECT status FROM projects WHERE id=?",String.class,id));assertTrue(events.values.isEmpty());
	}
	@Test void missingAndDeletedProjectsAreNotChanged() {
		long id=fixture();jdbc.update("UPDATE projects SET deleted_at=localtimestamp WHERE id=?",id);
		rejected(404,()->lifecycle.update(id,statusCommand(ProjectStatus.RECRUITMENT_COMPLETED)));
		rejected(404,()->lifecycle.update(Long.MAX_VALUE,statusCommand(ProjectStatus.RECRUITMENT_COMPLETED)));
	}
	static class CommittedEvents {
		final Queue<ProjectLifecycleChanged> values=new ConcurrentLinkedQueue<>();
		@TransactionalEventListener void collect(ProjectLifecycleChanged event){values.add(event);}
	}
	@TestConfiguration(proxyBeanMethods=false)
	static class EventsConfiguration { @Bean CommittedEvents lifecycleEvents(){return new CommittedEvents();} }
}
