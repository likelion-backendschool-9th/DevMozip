package com.devmozip.backend.project;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.devmozip.backend.security.jwt.*;
import com.devmozip.backend.service.ProjectPortfolioAccess;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.test.context.event.RecordApplicationEvents
@SpringBootTest(properties="project.current-user.development-enabled=false") @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers(disabledWithoutDocker=true)
class ProjectApplicationIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17");
	@Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper mapper;@Autowired PasswordEncoder encoder;@Autowired JwtProvider jwt;
	@Autowired org.springframework.test.context.event.ApplicationEvents events;
	@MockitoBean RefreshTokenStore refresh;
	@MockitoBean ProjectPortfolioAccess portfolios;
	@MockitoBean(name="projectClock") Clock clock;
	private String token(long id){return "Bearer "+jwt.createAccessToken(id+"@application.test",id==101?"PREMIUM":"USER");}
	@BeforeEach void reset(){
		jdbc.update("DELETE FROM project_application_portfolios");jdbc.update("DELETE FROM project_applications");jdbc.update("DELETE FROM review_keyword_selections"); jdbc.update("DELETE FROM reviews"); jdbc.update("DELETE FROM project_members");jdbc.update("DELETE FROM projects");jdbc.update("DELETE FROM users");
		for(long id:new long[]{101,202,303,404})jdbc.update("INSERT INTO users(id,email,password_hash,role,status) VALUES (?,?,?,?,?)",id,id+"@application.test",encoder.encode("Test1234!"),id==101?"PREMIUM":"USER","ACTIVE");
		when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));date("2026-11-06T12:00:00");
		when(portfolios.available()).thenReturn(true);when(portfolios.ownedBy(anyLong())).thenAnswer(i->List.of(new ProjectPortfolioAccess.Portfolio(i.getArgument(0,Long.class)*10,"본인 포트폴리오")));
		when(portfolios.ownsAll(anyLong(),anyList())).thenAnswer(i->{long user=i.getArgument(0);List<Long> ids=i.getArgument(1);return ids.stream().allMatch(id->id>=user*10&&id<=user*10+2);});
	}
	private void date(String text){when(clock.instant()).thenReturn(LocalDateTime.parse(text).atZone(ZoneId.of("Asia/Seoul")).toInstant());}
	private long project(int capacity) throws Exception {
		var body=new LinkedHashMap<String,Object>(Map.of("title","지원 검증 프로젝트","description","프로젝트 지원을 검증합니다.","imageUrl","https://example.test/a.png","isPublic",true,
			"recruitmentSlots",List.of(Map.of("roleCode","SRE","capacity",capacity,"minimumYears",0)),"techStacks",List.of("Java"),"interests",List.of("교육"),"recruitmentStart","2026-11-01","recruitmentEnd","2026-11-20","developmentStart","2026-11-06"));
		body.put("developmentEnd","2026-12-30");
		var r=mvc.perform(post("/api/v1/projects").header("Authorization",token(101)).contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse();return mapper.readTree(r.getContentAsString()).get("id").asLong();
	}
	private String base(long id){return "/api/v1/projects/"+id;}
	private long apply(long id,long user) throws Exception {
		var response=mvc.perform(post(base(id)+"/applications").header("Authorization",token(user)).contentType("application/json").content(mapper.writeValueAsString(Map.of("jobRole","sre","portfolioIds",List.of(user*10,user*10+1),"introduction","함께 프로젝트에 기여하고 싶습니다."))))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.applicantMemberId").value(user)).andReturn().getResponse();return mapper.readTree(response.getContentAsString()).get("id").asLong();
	}
	private org.springframework.test.web.servlet.ResultActions update(long id,long app,long user,String body) throws Exception {return mvc.perform(patch(base(id)+"/applications/"+app).header("Authorization",token(user)).contentType("application/json").content(body));}
	@Test void submitApproveAndCancelMaintainLinksAndMembers() throws Exception {
		long id=project(2),app=apply(id,202);
		assertEquals(2L,jdbc.queryForObject("SELECT count(*) FROM project_application_portfolios WHERE application_id=?",Long.class,app));
		mvc.perform(get("/api/v1/me/projects").param("tab","APPLYING").header("Authorization",token(202))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
		update(id,app,101,"{\"status\":\"APPROVED\"}").andExpect(status().isOk());
		mvc.perform(get(base(id)+"/members").header("Authorization",token(202)).param("jobRole","sre")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
		update(id,app,202,"{\"status\":\"CANCELLED\"}").andExpect(status().isOk());
		assertEquals("LEFT",jdbc.queryForObject("SELECT status FROM project_members WHERE project_id=? AND user_id=202",String.class,id));
		update(id,app,202,"{\"status\":\"CANCELLED\"}").andExpect(status().isConflict());
		mvc.perform(post(base(id)+"/applications").header("Authorization",token(202)).contentType("application/json").content(mapper.writeValueAsString(Map.of("jobRole","SRE","portfolioIds",List.of(2020),"introduction","다시 지원할 수 없어야 합니다.")))).andExpect(status().isConflict());
	}
	@Test void validatesPortfoliosAndFailClosesWhenUnavailable() throws Exception {
		long id=project(2);
		for(var ids:List.of(List.of(2020L,2020L),List.of(2020L,2021L,2022L,2023L),List.<Long>of()))mvc.perform(post(base(id)+"/applications").header("Authorization",token(202)).contentType("application/json").content(mapper.writeValueAsString(Map.of("jobRole","SRE","portfolioIds",ids,"introduction","본인 포트폴리오만 제출합니다.")))).andExpect(status().isBadRequest());
		mvc.perform(post(base(id)+"/applications").header("Authorization",token(202)).contentType("application/json").content(mapper.writeValueAsString(Map.of("jobRole","SRE","portfolioIds",List.of(3030),"introduction","본인 포트폴리오만 제출합니다.")))).andExpect(status().isForbidden());
		when(portfolios.available()).thenReturn(false);
		mvc.perform(get(base(id)+"/application-options").header("Authorization",token(202))).andExpect(status().isOk()).andExpect(jsonPath("$.canApply").value(false));
		mvc.perform(post(base(id)+"/applications").header("Authorization",token(202)).contentType("application/json").content(mapper.writeValueAsString(Map.of("jobRole","SRE","portfolioIds",List.of(2020),"introduction","포트폴리오 준비 전 차단합니다.")))).andExpect(status().isServiceUnavailable());
		assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM project_applications",Long.class));
	}
	@Test void enforcesPrivacyAndRejectionReason() throws Exception {
		long id=project(2),app=apply(id,202);
		mvc.perform(get(base(id)+"/applications").header("Authorization",token(303))).andExpect(status().isForbidden());
		update(id,app,303,"{\"status\":\"CANCELLED\"}").andExpect(status().isForbidden());
		update(id,app,101,"{\"status\":\"REJECTED\"}").andExpect(status().isBadRequest());
		update(id,app,101,"{\"status\":\"REJECTED\",\"rejectionReason\":\"요구 경험과 맞지 않습니다.\"}").andExpect(status().isOk()).andExpect(jsonPath("$.rejectionReason").value("요구 경험과 맞지 않습니다."));
		mvc.perform(get(base(id)+"/application-options").header("Authorization",token(202))).andExpect(status().isOk()).andExpect(jsonPath("$.application.rejectionReason").value("요구 경험과 맞지 않습니다."));
		mvc.perform(get(base(id))).andExpect(status().isOk()).andExpect(jsonPath("$.rejectionReason").doesNotExist());
	}
	@Test void sevenDayBoundaryUsesSeoulCalendarAndRequiresAcknowledgementOnlyAfterConfirmation() throws Exception {
		jdbc.update("UPDATE users SET trust_score=36.5");
		long id=project(3),a=apply(id,202),b=apply(id,303);update(id,a,101,"{\"status\":\"APPROVED\"}").andExpect(status().isOk());update(id,b,101,"{\"status\":\"APPROVED\"}").andExpect(status().isOk());
		date("2026-11-13T23:59:59");update(id,a,202,"{\"status\":\"CANCELLED\"}").andExpect(status().isOk());
		assertScore(202,"36.5");
		date("2026-11-14T00:00:00");update(id,b,303,"{\"status\":\"CANCELLED\"}").andExpect(status().isBadRequest());
		assertScore(303,"36.5");
		update(id,b,303,"{\"status\":\"CANCELLED\",\"acknowledgePenalty\":true}").andExpect(status().isOk());
		assertScore(303,"36.0");
		update(id,b,303,"{\"status\":\"CANCELLED\",\"acknowledgePenalty\":true}").andExpect(status().isConflict());
		assertScore(303,"36.0");
		assertEquals(1L,events.stream(com.devmozip.backend.event.ProjectApplicationChanged.class).filter(e->e.applicationId()==b&&e.penaltyRequired()).count());
	}
	private void assertScore(long user,String expected){assertEquals(0,new java.math.BigDecimal(expected).compareTo(jdbc.queryForObject("SELECT trust_score FROM users WHERE id=?",java.math.BigDecimal.class,user)));}
	@Test void pendingLateCancellationHasNoPenalty() throws Exception {
		long id=project(1),a=apply(id,202);jdbc.update("UPDATE users SET trust_score=36.5 WHERE id=202");date("2026-11-14T00:00:00");
		update(id,a,202,"{\"status\":\"CANCELLED\"}").andExpect(status().isOk());assertScore(202,"36.5");
	}
	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.CsvSource(value={"NULL,36.0","0.2,0","0,0"},nullValues="NULL")
	void latePenaltyUsesInitialScoreForNullAndNeverDropsBelowZero(String initial,String expected) throws Exception {
		long id=project(1),a=apply(id,202);update(id,a,101,"{\"status\":\"APPROVED\"}").andExpect(status().isOk());
		jdbc.update("UPDATE users SET trust_score=? WHERE id=202",initial==null?null:new java.math.BigDecimal(initial));date("2026-11-14T00:00:00");
		update(id,a,202,"{\"status\":\"CANCELLED\",\"acknowledgePenalty\":true}").andExpect(status().isOk());assertScore(202,expected);
	}
	@Test void penaltyFailureRollsBackCancellationAndMembership() throws Exception {
		long id=project(1),a=apply(id,202);update(id,a,101,"{\"status\":\"APPROVED\"}").andExpect(status().isOk());
		jdbc.update("UPDATE users SET trust_score=36.5 WHERE id=202");date("2026-11-14T00:00:00");
		jdbc.execute("ALTER TABLE users ADD CONSTRAINT test_penalty_failure CHECK (id<>202 OR trust_score>=36.5)");
		try {
			update(id,a,202,"{\"status\":\"CANCELLED\",\"acknowledgePenalty\":true}").andExpect(status().isInternalServerError());
			assertScore(202,"36.5");assertEquals("APPROVED",jdbc.queryForObject("SELECT status FROM project_applications WHERE id=?",String.class,a));
			assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM project_members WHERE project_id=? AND user_id=202",String.class,id));
		}finally{jdbc.execute("ALTER TABLE users DROP CONSTRAINT test_penalty_failure");}
	}
	@Test void concurrentCancellationsOnDifferentProjectsAccumulatePenalties() throws Exception {
		long one=project(1),two=project(1),a=apply(one,202),b=apply(two,202);
		update(one,a,101,"{\"status\":\"APPROVED\"}").andExpect(status().isOk());update(two,b,101,"{\"status\":\"APPROVED\"}").andExpect(status().isOk());
		jdbc.update("UPDATE users SET trust_score=36.5 WHERE id=202");date("2026-11-14T00:00:00");
		var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
		try {
			var first=pool.submit(()->{start.await();return update(one,a,202,"{\"status\":\"CANCELLED\",\"acknowledgePenalty\":true}").andReturn().getResponse().getStatus();});
			var second=pool.submit(()->{start.await();return update(two,b,202,"{\"status\":\"CANCELLED\",\"acknowledgePenalty\":true}").andReturn().getResponse().getStatus();});
			start.countDown();assertEquals(200,first.get(20,TimeUnit.SECONDS));assertEquals(200,second.get(20,TimeUnit.SECONDS));assertScore(202,"35.5");
		}finally{pool.shutdownNow();}
	}

	@Test void concurrentApprovalCannotExceedLastSeat() throws Exception {
		long id=project(1),a=apply(id,202),b=apply(id,303);var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
		try{List<Future<Integer>> results=new ArrayList<>();for(long app:new long[]{a,b})results.add(executor.submit(()->{start.await();return update(id,app,101,"{\"status\":\"APPROVED\"}").andReturn().getResponse().getStatus();}));start.countDown();var codes=results.stream().map(f->{try{return f.get(15,TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}}).sorted().toList();assertEquals(List.of(200,409),codes);assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM project_members WHERE project_id=? AND role='PARTICIPANT' AND status='ACTIVE'",Long.class,id));}finally{executor.shutdownNow();}
	}
	@Test void leavingRecalculatesEndQuorum() throws Exception {
		long id=project(1),app=apply(id,202);update(id,app,101,"{\"status\":\"APPROVED\"}").andExpect(status().isOk());jdbc.update("UPDATE projects SET status='IN_PROGRESS' WHERE id=?",id);jdbc.update("UPDATE project_members SET end_requested_at=localtimestamp WHERE project_id=? AND role='HOST'",id);
		update(id,app,202,"{\"status\":\"CANCELLED\"}").andExpect(status().isOk());assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM projects WHERE id=?",String.class,id));
	}
	@Test void duplicateSubmissionAndInvalidRoleDoNotCreateExtraRecords() throws Exception {
		long id=project(2);apply(id,202);
		mvc.perform(post(base(id)+"/applications").header("Authorization",token(202)).contentType("application/json").content(mapper.writeValueAsString(Map.of("jobRole","SRE","portfolioIds",List.of(2020),"introduction","중복 지원은 처리하지 않습니다.")))).andExpect(status().isConflict());
		mvc.perform(post(base(id)+"/applications").header("Authorization",token(303)).contentType("application/json").content(mapper.writeValueAsString(Map.of("jobRole","BACKEND","portfolioIds",List.of(3030),"introduction","등록하지 않은 직군 지원입니다.")))).andExpect(status().isConflict());
		mvc.perform(get(base(id)+"/members").header("Authorization",token(303))).andExpect(status().isForbidden());
		assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM project_applications",Long.class));
	}

}
