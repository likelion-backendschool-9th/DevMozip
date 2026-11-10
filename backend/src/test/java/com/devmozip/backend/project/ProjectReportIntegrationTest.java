package com.devmozip.backend.project;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="project.current-user.development-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.context.jdbc.Sql("classpath:project/lifecycle-applications.sql")
@Testcontainers(disabledWithoutDocker=true)
class ProjectReportIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17");
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper mapper;
	@Autowired PasswordEncoder encoder;
	@MockitoBean RefreshTokenStore refresh;
	private static final String PASSWORD="Test-member123!";
	@BeforeEach void reset(){
		jdbc.update("DELETE FROM reports");jdbc.update("DELETE FROM likes");jdbc.update("DELETE FROM bookmarks");jdbc.update("DELETE FROM project_applications");jdbc.update("DELETE FROM project_members");jdbc.update("DELETE FROM projects");jdbc.update("DELETE FROM users");
		for(long id:new long[]{101,202,303})jdbc.update("INSERT INTO users(id,email,password_hash,role,status) VALUES (?,?,?,?,?)",id,id+"@project.test",encoder.encode(PASSWORD),id==101?"PREMIUM":"USER","ACTIVE");
	}
	private String login(long id) throws Exception {
		var response=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(mapper.writeValueAsString(Map.of("username",id+"@project.test","password",PASSWORD))))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return "Bearer "+mapper.readTree(response).get("accessToken").asText();
	}
	private long create(String token) throws Exception {
		LocalDate today=LocalDate.now(ZoneId.of("Asia/Seoul"));
		String body="""
			{"title":"실제 회원의 프로젝트","description":"JWT 이메일과 DB 회원 ID 연결 검증",
			"imageUrl":"https://example.test/image.png","isPublic":true,"hostMemberId":202,
			"recruitmentSlots":[{"roleCode":"BACKEND","capacity":2,"minimumYears":0}],
			"techStacks":["Java"],"interests":["교육"],"recruitmentStart":"%s","recruitmentEnd":"%s",
			"developmentStart":"%s","developmentEnd":"%s"}
			""".formatted(today,today.plusDays(7),today,today.plusDays(90));
		var response=mvc.perform(post("/api/v1/projects").header("Authorization",token).contentType("application/json").content(body))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.hostMemberId").value(101)).andReturn().getResponse().getContentAsString();
		return mapper.readTree(response).get("id").asLong();
	}
	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder report(long id,String token,Object body) throws Exception {
		return post("/api/v1/projects/"+id+"/reports").header("Authorization",token).contentType("application/json").content(mapper.writeValueAsString(body));
	}
	@Test void savesOnlyProjectTargetAndPreservesOtherKindsOfReports() throws Exception {
		String host=login(101),user=login(202);long id=create(host);
		jdbc.update("INSERT INTO reports(reporter_id,reported_user_id,reason,status,created_at,updated_at) VALUES (202,101,'기타','RECEIVED',localtimestamp,localtimestamp)");
		jdbc.update("INSERT INTO reports(reporter_id,post_id,reason,status,created_at,updated_at) VALUES (202,501,'기타','RECEIVED',localtimestamp,localtimestamp)");
		mvc.perform(report(id,user,Map.of("reason","허위 모집 정보","detail","설명과 모집 내용이 다릅니다.","reporterId",101,"reportedUserId",101)))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.projectId").value(id)).andExpect(jsonPath("$.status").value("RECEIVED")).andExpect(jsonPath("$.id").isNumber());
		var saved=jdbc.queryForMap("SELECT * FROM reports WHERE project_id=?",id);
		assertEquals(202L,saved.get("reporter_id"));assertNull(saved.get("reported_user_id"));assertNull(saved.get("post_id"));assertNull(saved.get("comment_id"));assertNull(saved.get("handled_by"));assertNull(saved.get("handled_at"));assertNull(saved.get("resolution"));
		assertEquals("허위 모집 정보\n\n설명과 모집 내용이 다릅니다.",saved.get("reason"));assertNotNull(saved.get("created_at"));assertNotNull(saved.get("updated_at"));
		assertEquals(2L,jdbc.queryForObject("SELECT count(*) FROM reports WHERE project_id IS NULL",Long.class));
		mvc.perform(report(id,user,Map.of("reason","기타"))).andExpect(status().isConflict());
		mvc.perform(report(id,login(303),Map.of("reason","기타"))).andExpect(status().isCreated());
	}
	@Test void requiresAuthenticationValidReasonAndValidStringDetail() throws Exception {
		long id=create(login(101));String user=login(202);
		mvc.perform(post("/api/v1/projects/"+id+"/reports").contentType("application/json").content("{\"reason\":\"기타\"}")).andExpect(status().isUnauthorized());
		for(Object body:new Object[]{Map.of(),Map.of("reason","없는 사유"),Map.of("reason","기타","detail","가".repeat(1001)),Map.of("reason","기타","detail",123)})mvc.perform(report(id,user,body)).andExpect(status().isBadRequest());
		mvc.perform(report(0,user,Map.of("reason","기타"))).andExpect(status().isBadRequest());
		assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM reports",Long.class));
	}
	@Test void rejectsOwnMissingAndDeletedProjectsButKeepsExistingReportHistory() throws Exception {
		String host=login(101),user=login(202);long id=create(host);
		mvc.perform(report(id,host,Map.of("reason","기타"))).andExpect(status().isBadRequest());
		mvc.perform(report(999999,user,Map.of("reason","기타"))).andExpect(status().isNotFound());
		mvc.perform(report(id,user,Map.of("reason","기타"))).andExpect(status().isCreated());
		jdbc.update("UPDATE projects SET deleted_at=localtimestamp WHERE id=?",id);
		mvc.perform(report(id,login(303),Map.of("reason","기타"))).andExpect(status().isNotFound());
		assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM reports WHERE project_id=?",Long.class,id));
	}
	@Test void privateProjectRequiresCurrentActiveMembership() throws Exception {
		long id=create(login(101));String user=login(202);jdbc.update("UPDATE projects SET is_public=false WHERE id=?",id);
		mvc.perform(report(id,user,Map.of("reason","기타"))).andExpect(status().isNotFound());
		jdbc.update("INSERT INTO project_members(project_id,user_id,role,job_role,status,joined_at,created_at,updated_at) VALUES (?,202,'PARTICIPANT','BACKEND','ACTIVE',localtimestamp,localtimestamp,localtimestamp)",id);
		mvc.perform(report(id,user,Map.of("reason","기타"))).andExpect(status().isCreated());
		jdbc.update("UPDATE project_members SET status='LEFT' WHERE project_id=? AND user_id=202",id);
		mvc.perform(report(id,user,Map.of("reason","기타"))).andExpect(status().isNotFound());
	}
	@Test void concurrentDuplicatesAreRejectedAndUserReportsDoNotBlockProjectReports() throws Exception {
		long id=create(login(101));String user=login(202);
		try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
			var start=new java.util.concurrent.CountDownLatch(1);
			java.util.concurrent.Callable<Integer> send=()->{start.await();return mvc.perform(report(id,user,Map.of("reason","기타"))).andReturn().getResponse().getStatus();};
			var first=executor.submit(send);var second=executor.submit(send);start.countDown();
			assertEquals(java.util.Set.of(201,409),java.util.Set.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),second.get(10,java.util.concurrent.TimeUnit.SECONDS)));
		}
		assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM reports WHERE project_id=?",Long.class,id));
	}
	@Test void suspendedAccountCannotReportWithOldToken() throws Exception {
		long id=create(login(101));String user=login(202);jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=202");
		mvc.perform(report(id,user,Map.of("reason","기타"))).andExpect(status().isForbidden());
	}
}
