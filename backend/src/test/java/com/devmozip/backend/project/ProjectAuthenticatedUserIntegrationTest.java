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
class ProjectAuthenticatedUserIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17");
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper mapper;
	@Autowired PasswordEncoder encoder;
	@MockitoBean RefreshTokenStore refresh;
	private static final String PASSWORD="Test-member123!";
	@BeforeEach void reset(){
		jdbc.update("DELETE FROM project_applications");jdbc.update("DELETE FROM review_keyword_selections"); jdbc.update("DELETE FROM reviews"); jdbc.update("DELETE FROM project_members");jdbc.update("DELETE FROM projects");jdbc.update("DELETE FROM users");
		for(long id:new long[]{101,202})jdbc.update("INSERT INTO users(id,email,password_hash,role,status) VALUES (?,?,?,?,?)",id,id+"@project.test",encoder.encode(PASSWORD),id==101?"PREMIUM":"USER","ACTIVE");
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
	@Test void realLoginCreatesHostAndSeparatesOwnProjectsAndManagement() throws Exception {
		String host=login(101),other=login(202);long id=create(host);
		assertEquals(101L,jdbc.queryForObject("SELECT user_id FROM project_members WHERE project_id=? AND role='HOST'",Long.class,id));
		mvc.perform(get("/api/v1/me/projects").header("Authorization",host)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(get("/api/v1/me/projects").header("Authorization",other)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get("/api/v1/projects/"+id+"/manage").header("Authorization",host)).andExpect(status().isOk());
		mvc.perform(get("/api/v1/projects/"+id+"/manage").header("Authorization",other)).andExpect(status().isForbidden());
		mvc.perform(delete("/api/v1/projects/"+id).header("Authorization",other)).andExpect(status().isForbidden());
		mvc.perform(patch("/api/v1/projects/"+id+"/update").header("Authorization",host).contentType("application/json").content("{\"action\":\"CHANGE_STATUS\",\"status\":\"RECRUITMENT_COMPLETED\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.project.hostMemberId").value(101));
	}
	@Test void blockedAndDeletedMembersCannotUseOldTokensButWarnedMembersCan() throws Exception {
		String token=login(202);
		jdbc.update("UPDATE users SET status='WARNED' WHERE id=202");
		mvc.perform(get("/api/v1/me/projects").header("Authorization",token)).andExpect(status().isOk());
		jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=202");
		mvc.perform(get("/api/v1/me/projects").header("Authorization",token)).andExpect(status().isForbidden());
		jdbc.update("DELETE FROM users WHERE id=202");
		mvc.perform(get("/api/v1/me/projects").header("Authorization",token)).andExpect(status().isUnauthorized());
	}
	@Test void anonymousPublicReadsStillWorkAndOwnListRequiresLogin() throws Exception {
		mvc.perform(get("/api/v1/projects")).andExpect(status().isOk());
		mvc.perform(get("/api/v1/me/projects")).andExpect(status().isUnauthorized());
	}
	@Test void freeMemberCannotCreateButPremiumMemberCan() throws Exception {
		String free=login(202);
		LocalDate today=LocalDate.now(ZoneId.of("Asia/Seoul"));
		String body="""
			{"title":"무료 공고","description":"권한 검사 공고","imageUrl":"https://example.test/a.png","isPublic":true,
			"recruitmentSlots":[{"roleCode":"BACKEND","capacity":1,"minimumYears":0}],"techStacks":[],"interests":[],
			"recruitmentStart":"%s","recruitmentEnd":"%s","developmentStart":"%s","developmentEnd":"%s"}
			""".formatted(today,today.plusDays(2),today,today.plusDays(90));
		mvc.perform(post("/api/v1/projects").header("Authorization",free).contentType("application/json").content(body)).andExpect(status().isForbidden());
		assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM projects",Long.class));
		create(login(101));
	}

	@Test void realApplicationsProtectTheirRecruitmentRole() throws Exception {
		String host=login(101);long id=create(host);
		String response=mvc.perform(get("/api/v1/projects/"+id).header("Authorization",host)).andReturn().getResponse().getContentAsString();
		String update=mapper.readTree(response).get("project").toString().replace("BACKEND","FRONTEND");
		jdbc.update("INSERT INTO project_applications(project_id,applicant_member_id,job_role,status,applied_at,created_at,updated_at) VALUES (?,202,'BACKEND','PENDING',localtimestamp,localtimestamp,localtimestamp)",id);
		mvc.perform(put("/api/v1/projects/"+id).header("Authorization",host).contentType("application/json").content(update)).andExpect(status().isConflict());
		jdbc.update("UPDATE project_applications SET status='APPROVED' WHERE project_id=?",id);
		mvc.perform(put("/api/v1/projects/"+id).header("Authorization",host).contentType("application/json").content(update)).andExpect(status().isConflict());
		jdbc.update("UPDATE project_applications SET status='REJECTED' WHERE project_id=?",id);
		mvc.perform(put("/api/v1/projects/"+id).header("Authorization",host).contentType("application/json").content(update)).andExpect(status().isOk());
	}
	@Test void publicDetailDoesNotExposeMembershipButHostCanSeeOwnState() throws Exception {
		String host=login(101),other=login(202);long id=create(host);
		mvc.perform(get("/api/v1/projects/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.membership").doesNotExist());
		mvc.perform(get("/api/v1/projects/"+id).header("Authorization",other)).andExpect(status().isOk()).andExpect(jsonPath("$.membership").doesNotExist());
		mvc.perform(get("/api/v1/projects/"+id).header("Authorization",host)).andExpect(status().isOk()).andExpect(jsonPath("$.membership.myRole").value("HOST")).andExpect(jsonPath("$.membership.myEndRequested").value(false)).andExpect(jsonPath("$.membership.requiredEndRequestCount").value(1));
	}

}
