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
class ProjectReviewIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17");
	@Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper mapper;@Autowired PasswordEncoder encoder;@Autowired JwtProvider jwt;
	@Autowired org.springframework.test.context.event.ApplicationEvents events;
 @Autowired com.devmozip.backend.service.ProjectReviewService reviewService;
 @Autowired org.springframework.context.ApplicationEventPublisher publisher;
 @Autowired com.devmozip.backend.repository.ReviewKeywordRepository keywords;
	@MockitoBean RefreshTokenStore refresh;
	@MockitoBean ProjectPortfolioAccess portfolios;
	@MockitoBean(name="projectClock") Clock clock;
	private String token(long id){return "Bearer "+jwt.createAccessToken(id+"@application.test",id==101?"PREMIUM":"USER");}
	@BeforeEach void reset(){
		jdbc.update("DELETE FROM project_application_portfolios");jdbc.update("DELETE FROM project_applications");jdbc.update("DELETE FROM review_keyword_selections"); jdbc.update("DELETE FROM reviews"); jdbc.update("DELETE FROM project_members");jdbc.update("DELETE FROM projects");jdbc.update("DELETE FROM users");
		for(long id:new long[]{101,202,303,404})jdbc.update("INSERT INTO users(id,email,password_hash,role,status) VALUES (?,?,?,?,?)",id,id+"@application.test",encoder.encode("Test1234!"),id==101?"PREMIUM":"USER","ACTIVE");
		when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));date("2026-11-06T12:00:00");
		jdbc.update("DELETE FROM review_keywords");
		for(String name:List.of("소통","책임감"))jdbc.update("INSERT INTO review_keywords(name,is_active,created_at,updated_at) VALUES (?,true,localtimestamp,localtimestamp)",name);
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

 private long ended() throws Exception {long id=project(2);for(long user:new long[]{202,303})jdbc.update("INSERT INTO project_members(project_id,user_id,role,job_role,status,joined_at,created_at,updated_at) VALUES (?,?,'PARTICIPANT','SRE','ACTIVE',localtimestamp,localtimestamp,localtimestamp)",id,user);jdbc.update("UPDATE projects SET status='IN_PROGRESS' WHERE id=?",id);for(long user:new long[]{101,202})mvc.perform(patch(base(id)+"/update").header("Authorization",token(user)).contentType("application/json").content("{\"action\":\"REQUEST_END\"}")).andExpect(status().isOk());return id;}
 private tools.jackson.databind.JsonNode mine(long id,long user) throws Exception {return mapper.readTree(mvc.perform(get(base(id)+"/reviews/me").header("Authorization",token(user))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 private String link(long id,long user) throws Exception {var body=mapper.readTree(mvc.perform(post(base(id)+"/reviews/me/link").header("Authorization",token(user))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());return body.get("href").asText().split("token=")[1];}
 private String submission(long id,long user,String token) throws Exception {var entries=new ArrayList<Map<String,Object>>();for(var target:mine(id,user).get("targets"))entries.add(Map.of("targetMemberId",target.get("memberId").asLong(),"rating",5,"keywords",List.of("소통")));return mapper.writeValueAsString(Map.of("token",token,"entries",entries));}
 private org.springframework.test.web.servlet.ResultActions submit(long id,long user,String body) throws Exception {return mvc.perform(put(base(id)+"/reviews/me").header("Authorization",token(user)).contentType("application/json").content(body));}
 @Test void completionSnapshotsTargetsAndSubmissionIsImmutable() throws Exception {
  long id=ended();assertEquals(6L,jdbc.queryForObject("SELECT count(*) FROM reviews WHERE project_id=?",Long.class,id));assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM reviews WHERE reviewer_member_id=reviewee_member_id",Long.class));
  assertEquals(3L,events.stream(com.devmozip.backend.event.ProjectReviewRequested.class).filter(e->!e.reminder()).count());
  assertEquals(1L,jdbc.queryForObject("SELECT count(DISTINCT token_hash) FROM reviews r JOIN project_members m ON m.id=r.reviewer_member_id WHERE m.user_id=202",Long.class));
  var before=mine(id,202);assertEquals("2026-12-06T12:00:00",before.get("expiresAt").asText());
  String token=link(id,202),body=submission(id,202,token);submit(id,202,body).andExpect(status().isOk()).andExpect(jsonPath("$.submitted").value(true));submit(id,202,body).andExpect(status().isConflict());
  assertEquals(2L,jdbc.queryForObject("SELECT count(*) FROM reviews WHERE status='SUBMITTED'",Long.class));assertEquals(1L,reviewService.received(101).count());assertEquals(5.0,reviewService.received(101).average());
 }
 @Test void tokenRotationAndOtherMemberAreRejected() throws Exception {long id=ended();String old=link(id,202);String body=submission(id,202,old);link(id,202);submit(id,202,body).andExpect(status().isForbidden());submit(id,303,body).andExpect(status().isForbidden());mvc.perform(get(base(id)+"/reviews/me").header("Authorization",token(404))).andExpect(status().isForbidden());assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM reviews WHERE status='SUBMITTED'",Long.class));}
 @Test void exactThirtyDayBoundaryAndReminderDeduplication() throws Exception {long id=ended();String token=link(id,202),body=submission(id,202,token);
  date("2026-11-09T11:59:59");mvc.perform(post(base(id)+"/reviews/reminders").header("Authorization",token(101))).andExpect(jsonPath("$.requested").value(0));date("2026-11-09T12:00:00");
  mvc.perform(post(base(id)+"/reviews/reminders").header("Authorization",token(101))).andExpect(status().isOk()).andExpect(jsonPath("$.requested").value(3));mvc.perform(post(base(id)+"/reviews/reminders").header("Authorization",token(101))).andExpect(jsonPath("$.requested").value(0));
  token=link(id,202);body=submission(id,202,token);date("2026-12-06T12:00:00");submit(id,202,body).andExpect(status().isGone());mvc.perform(post(base(id)+"/reviews/reminders").header("Authorization",token(101))).andExpect(jsonPath("$.requested").value(0));
 }
 @Test void invalidAndPartialSubmissionsAreAtomicAndKeywordsOptional() throws Exception {long id=ended();String token=link(id,202);var targets=mine(id,202).get("targets");long member=targets.get(0).get("memberId").asLong();
  String partial=mapper.writeValueAsString(Map.of("token",token,"entries",List.of(Map.of("targetMemberId",member,"rating",5,"keywords",List.of()))));submit(id,202,partial).andExpect(status().isBadRequest());
  submit(id,202,submission(id,202,token).replace("소통","알수없음")).andExpect(status().isBadRequest());submit(id,202,submission(id,202,token).replace("\"rating\":5","\"rating\":0")).andExpect(status().isBadRequest());
  assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM reviews WHERE status='SUBMITTED'",Long.class));
  submit(id,202,submission(id,202,token).replace("[\"소통\"]","[]")).andExpect(status().isOk());assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM review_keyword_selections",Long.class));date("2026-11-09T12:00:00");mvc.perform(post(base(id)+"/reviews/reminders").header("Authorization",token(101))).andExpect(jsonPath("$.requested").value(2));
 }
 @Test void completionReplayDoesNotDuplicateOrChangeSnapshot() throws Exception {long id=ended();long count=jdbc.queryForObject("SELECT count(*) FROM reviews",Long.class);new org.springframework.transaction.support.TransactionTemplate(transactionManager()).executeWithoutResult(s->publisher.publishEvent(new com.devmozip.backend.event.ProjectLifecycleChanged(com.devmozip.backend.event.ProjectLifecycleChanged.Type.COMPLETED,id,101,com.devmozip.backend.entity.enums.ProjectStatus.IN_PROGRESS,com.devmozip.backend.entity.enums.ProjectStatus.COMPLETED,null,null,null,null,LocalDateTime.now())));assertEquals(count,jdbc.queryForObject("SELECT count(*) FROM reviews",Long.class));}
 @Autowired org.springframework.transaction.PlatformTransactionManager manager;private org.springframework.transaction.PlatformTransactionManager transactionManager(){return manager;}
 @Test void concurrentWholeSubmissionOnlyCommitsOnce() throws Exception {long id=ended();String body=submission(id,202,link(id,202));var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);try{var results=new ArrayList<Future<Integer>>();for(int i=0;i<2;i++)results.add(pool.submit(()->{start.await();return submit(id,202,body).andReturn().getResponse().getStatus();}));start.countDown();var codes=results.stream().map(f->{try{return f.get(15,TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}}).sorted().toList();assertEquals(List.of(200,409),codes);assertEquals(2L,jdbc.queryForObject("SELECT count(*) FROM reviews WHERE status='SUBMITTED'",Long.class));}finally{pool.shutdownNow();}}
 @Test void lastSecondAllowedAndSnapshotRemainsAfterLeaving() throws Exception {long id=ended();String body=submission(id,202,link(id,202));jdbc.update("UPDATE project_members SET status='LEFT',left_at=localtimestamp WHERE project_id=? AND user_id=202",id);date("2026-12-06T11:59:59");submit(id,202,body).andExpect(status().isOk());assertEquals(2,mine(id,202).get("targets").size());mvc.perform(get(base(id)+"/reviews/status").header("Authorization",token(303))).andExpect(status().isForbidden());}

 @Test void databaseKeywordsOnlyAllowActiveNamesAndKeepInactiveSelections() throws Exception {
  long id=ended();jdbc.update("UPDATE review_keywords SET is_active=false WHERE name='책임감'");
  assertEquals(1,mine(id,202).get("keywords").size());assertEquals("소통",mine(id,202).get("keywords").get(0).asText());
  String token=link(id,202),body=submission(id,202,token);
  submit(id,202,body.replace("소통","책임감")).andExpect(status().isBadRequest());
  submit(id,202,body).andExpect(status().isOk());
  jdbc.update("UPDATE review_keywords SET name='소통 잘해요',is_active=false WHERE name='소통'");
  assertEquals("소통 잘해요",mine(id,202).get("targets").get(0).get("keywords").get(0).asText());
  String next=submission(id,303,link(id,303));submit(id,303,next).andExpect(status().isBadRequest());
 }

 @Test void maximumFiveKeywordsPersistRealForeignKeysAndRejectSixAtomically() throws Exception {
  long id=ended();for(String name:List.of("꼼꼼해요","친절해요","적극적이에요","성실해요"))jdbc.update("INSERT INTO review_keywords(name,is_active,created_at,updated_at) VALUES (?,true,localtimestamp,localtimestamp)",name);
  String original=submission(id,202,link(id,202));
  var five=List.of("소통","책임감","꼼꼼해요","친절해요","적극적이에요");var six=new ArrayList<>(five);six.add("성실해요");
  submit(id,202,original.replace("[\"소통\"]",mapper.writeValueAsString(six))).andExpect(status().isBadRequest());
  assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM review_keyword_selections",Long.class));assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM reviews WHERE status='SUBMITTED'",Long.class));
  submit(id,202,original.replace("[\"소통\"]",mapper.writeValueAsString(five))).andExpect(status().isOk());
  assertEquals(10L,jdbc.queryForObject("SELECT count(*) FROM review_keyword_selections s JOIN review_keywords k ON k.id=s.keyword_id JOIN reviews r ON r.id=s.review_id",Long.class));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO review_keyword_selections(review_id,keyword_id,created_at,updated_at) SELECT review_id,keyword_id,localtimestamp,localtimestamp FROM review_keyword_selections LIMIT 1"));
 }

 @Test void reviewsHaveNoDuplicateKeywordColumnAndReadSelectionsOnly() throws Exception {
  assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='reviews' AND column_name='selected_keywords'",Long.class));
  long id=ended();submit(id,202,submission(id,202,link(id,202))).andExpect(status().isOk());
  assertEquals(2L,jdbc.queryForObject("SELECT count(*) FROM review_keyword_selections",Long.class));
  assertEquals("소통",reviewService.received(101).keywords().getFirst().name());
 }

}
