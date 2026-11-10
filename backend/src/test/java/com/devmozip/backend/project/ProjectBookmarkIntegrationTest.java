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
class ProjectBookmarkIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17");
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper mapper;
	@Autowired PasswordEncoder encoder;
	@MockitoBean RefreshTokenStore refresh;
	private static final String PASSWORD="Test-member123!";
	@BeforeEach void reset(){
		jdbc.update("DELETE FROM likes");jdbc.update("DELETE FROM bookmarks");jdbc.update("DELETE FROM project_applications");jdbc.update("DELETE FROM project_members");jdbc.update("DELETE FROM projects");jdbc.update("DELETE FROM users");
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
	@Test void repeatedSaveAndRemoveAreIdempotentAndUserIsolated() throws Exception {
		String host=login(101),other=login(202);long id=create(host);String path="/api/v1/projects/"+id+"/bookmark";
		for(int i=0;i<2;i++)mvc.perform(post(path).header("Authorization",other)).andExpect(status().isOk()).andExpect(jsonPath("$.bookmarked").value(true));
		assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM bookmarks WHERE user_id=202 AND project_id=? AND post_id IS NULL",Long.class,id));
		mvc.perform(get("/api/v1/projects/bookmarks").header("Authorization",other)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
		mvc.perform(get("/api/v1/projects/bookmarks").header("Authorization",host)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(delete(path).header("Authorization",host)).andExpect(status().isOk());
		assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM bookmarks",Long.class));
		jdbc.update("INSERT INTO bookmarks(user_id,post_id,created_at,updated_at) VALUES (202,123,localtimestamp,localtimestamp)");
		for(int i=0;i<2;i++)mvc.perform(delete(path).header("Authorization",other)).andExpect(status().isOk()).andExpect(jsonPath("$.bookmarked").value(false));
		assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM bookmarks WHERE post_id=123",Long.class));
	}
	@Test void deletedAndPrivateProjectsAreHiddenAndOwnRecordsRemainRemovable() throws Exception {
		String host=login(101),other=login(202);long id=create(host);String path="/api/v1/projects/"+id+"/bookmark";
		mvc.perform(post(path).header("Authorization",other)).andExpect(status().isOk());
		jdbc.update("UPDATE projects SET is_public=false WHERE id=?",id);
		mvc.perform(get("/api/v1/projects/bookmarks").header("Authorization",other)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(post(path).header("Authorization",other)).andExpect(status().isNotFound());
		mvc.perform(post(path).header("Authorization",host)).andExpect(status().isOk());
		mvc.perform(get("/api/v1/projects/bookmarks").header("Authorization",host)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
		jdbc.update("UPDATE projects SET deleted_at=localtimestamp WHERE id=?",id);
		mvc.perform(get("/api/v1/projects/bookmarks").header("Authorization",host)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(post(path).header("Authorization",host)).andExpect(status().isNotFound());
		mvc.perform(delete(path).header("Authorization",other)).andExpect(status().isOk());
	}
	@Test void anonymousAndInvalidTargetsCannotBeSaved() throws Exception {
		mvc.perform(get("/api/v1/projects/bookmarks")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/projects/1/bookmark")).andExpect(status().isUnauthorized());
		String token=login(202);
		mvc.perform(post("/api/v1/projects/0/bookmark").header("Authorization",token)).andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/projects/999999/bookmark").header("Authorization",token)).andExpect(status().isNotFound());
	}
	@Test void concurrentSavesCreateOnlyOneRecord() throws Exception {
		String host=login(101),other=login(202);long id=create(host);
		try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
			var start=new java.util.concurrent.CountDownLatch(1);
			java.util.concurrent.Callable<Integer> save=()->{start.await();return mvc.perform(post("/api/v1/projects/"+id+"/bookmark").header("Authorization",other)).andReturn().getResponse().getStatus();};
			var first=executor.submit(save);var second=executor.submit(save);start.countDown();
			assertEquals(200,first.get(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(200,second.get(10,java.util.concurrent.TimeUnit.SECONDS));
		}
		assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM bookmarks WHERE user_id=202 AND project_id=?",Long.class,id));
	}

}
