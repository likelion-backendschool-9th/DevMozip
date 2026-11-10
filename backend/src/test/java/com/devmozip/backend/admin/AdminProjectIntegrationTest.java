package com.devmozip.backend.admin;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * WBS 14.4 프로젝트 공고 관리, 14.5 표시 여부 (docs/admin-plan.md 4단계).
 * 관리자 숨김 공고가 비공개 공고처럼 목록·검색·상세·북마크에서 팀원 외에는 사라지는지, 삭제가 주최자 삭제와 같은지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.context.jdbc.Sql("classpath:project/lifecycle-applications.sql")
@Testcontainers(disabledWithoutDocker = true)
class AdminProjectIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper mapper;
	@Autowired PasswordEncoder encoder;

	static final String ADMIN_PROJECTS = "/api/v1/admin/projects";
	static final String PASSWORD = "Test-member123!";
	static final long ADMIN = 1, HOST = 101, MEMBER = 202, OUTSIDER = 303;

	@BeforeEach
	void reset() {
		jdbc.update("DELETE FROM reports");
		jdbc.update("DELETE FROM likes");
		jdbc.update("DELETE FROM bookmarks");
		jdbc.update("DELETE FROM project_applications");
		jdbc.update("DELETE FROM project_members");
		jdbc.update("DELETE FROM projects");
		jdbc.update("DELETE FROM users");
		user(ADMIN, "ADMIN", "운영자", "admin");
		user(HOST, "PREMIUM", "주최자", "host");
		user(MEMBER, "USER", "팀원", "member");
		user(OUTSIDER, "USER", "방문자", "outsider");
	}

	void user(long id, String role, String nickname, String tag) {
		jdbc.update("INSERT INTO users(id, email, password_hash, role, status, nickname, tag, is_private) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, false)",
			id, id + "@admin-project.test", encoder.encode(PASSWORD), role, nickname, tag);
	}

	String login(long id) throws Exception {
		String response = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
				.content(mapper.writeValueAsString(Map.of("username", id + "@admin-project.test", "password", PASSWORD))))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return "Bearer " + mapper.readTree(response).get("accessToken").asText();
	}

	String body(String title) {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		return """
			{"title":"%s","description":"관리자 공고 관리 검증","imageUrl":"https://example.test/image.png","isPublic":true,
			"recruitmentSlots":[{"roleCode":"BACKEND","capacity":2,"minimumYears":0}],
			"techStacks":["Java"],"interests":["교육"],"recruitmentStart":"%s","recruitmentEnd":"%s",
			"developmentStart":"%s","developmentEnd":"%s"}
			""".formatted(title, today, today.plusDays(7), today, today.plusDays(90));
	}

	long create(String host, String title) throws Exception {
		String response = mvc.perform(post("/api/v1/projects").header("Authorization", host).contentType("application/json").content(body(title)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return mapper.readTree(response).get("id").asLong();
	}

	void join(long project, long user) {
		jdbc.update("INSERT INTO project_members(project_id, user_id, role, job_role, status, joined_at, created_at, updated_at)"
			+ " VALUES (?, ?, 'PARTICIPANT', 'BACKEND', 'ACTIVE', localtimestamp, localtimestamp, localtimestamp)", project, user);
	}

	MockHttpServletRequestBuilder restrict(String admin, long id, boolean restricted) {
		return patch(ADMIN_PROJECTS + "/" + id + "/visibility").header("Authorization", admin).contentType("application/json")
			.content("{\"restricted\":" + restricted + "}");
	}

	@Test
	void listsProjectsWithHostAndFilters() throws Exception {
		String host = login(HOST), admin = login(ADMIN);
		long first = create(host, "첫 공고"), second = create(host, "100% 원격 공고");
		jdbc.update("UPDATE projects SET status = 'IN_PROGRESS', is_public = false WHERE id = ?", first);
		mvc.perform(get(ADMIN_PROJECTS).header("Authorization", admin)).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.items[0].id").value(second))
			.andExpect(jsonPath("$.items[0].hostNickname").value("주최자"))
			.andExpect(jsonPath("$.items[0].hostTag").value("host"))
			.andExpect(jsonPath("$.items[0].isPublic").value(true))
			.andExpect(jsonPath("$.items[0].restricted").value(false))
			.andExpect(jsonPath("$.items[1].isPublic").value(false));
		mvc.perform(get(ADMIN_PROJECTS).header("Authorization", admin).param("status", "IN_PROGRESS"))
			.andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(first));
		mvc.perform(get(ADMIN_PROJECTS).header("Authorization", admin).param("q", "100%"))
			.andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(second));
		mvc.perform(get(ADMIN_PROJECTS).header("Authorization", admin).param("restricted", "true")).andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get(ADMIN_PROJECTS).header("Authorization", admin).param("restricted", "yes")).andExpect(status().isBadRequest());
		mvc.perform(get(ADMIN_PROJECTS).header("Authorization", admin).param("status", "DONE")).andExpect(status().isBadRequest());
		mvc.perform(get(ADMIN_PROJECTS).header("Authorization", host)).andExpect(status().isForbidden());
	}

	@Test
	void restrictedProjectIsVisibleOnlyToTeamMembersUntilRestored() throws Exception {
		String host = login(HOST), admin = login(ADMIN), member = login(MEMBER), outsider = login(OUTSIDER);
		long id = create(host, "숨길 공고");
		join(id, MEMBER);
		mvc.perform(restrict(admin, id, true)).andExpect(status().isOk()).andExpect(jsonPath("$.restricted").value(true));
		mvc.perform(restrict(admin, id, true)).andExpect(status().isOk());

		mvc.perform(get("/api/v1/projects")).andExpect(jsonPath("$.items[*].id", not(hasItem((int) id))));
		mvc.perform(get("/api/v1/search").param("type", "projects")).andExpect(status().isOk())
			.andExpect(jsonPath("$..id", not(hasItem((int) id))));
		mvc.perform(get("/api/v1/projects/" + id)).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/projects/" + id).header("Authorization", outsider)).andExpect(status().isNotFound());
		mvc.perform(post("/api/v1/projects/" + id + "/bookmark").header("Authorization", outsider)).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/projects/" + id).header("Authorization", member)).andExpect(status().isOk());
		mvc.perform(get("/api/v1/projects/" + id).header("Authorization", host)).andExpect(status().isOk());

		// 주최자가 공고를 고쳐 공개로 두어도 관리자 숨김은 풀리지 않는다.
		mvc.perform(put("/api/v1/projects/" + id).header("Authorization", host).contentType("application/json").content(body("고친 공고")))
			.andExpect(status().isOk());
		assertTrue(jdbc.queryForObject("SELECT is_restricted FROM projects WHERE id = ?", Boolean.class, id));
		mvc.perform(get("/api/v1/projects/" + id)).andExpect(status().isNotFound());

		mvc.perform(restrict(admin, id, false)).andExpect(status().isOk()).andExpect(jsonPath("$.restricted").value(false));
		mvc.perform(get("/api/v1/projects/" + id)).andExpect(status().isOk());
		mvc.perform(get("/api/v1/projects")).andExpect(jsonPath("$.items[*].id", hasItem((int) id)));
	}

	@Test
	void deletesOnlyRecruitingProjectsLikeHostDeletion() throws Exception {
		String host = login(HOST), admin = login(ADMIN);
		long recruiting = create(host, "모집 중"), running = create(host, "진행 중");
		jdbc.update("UPDATE projects SET status = 'IN_PROGRESS' WHERE id = ?", running);
		mvc.perform(put(ADMIN_PROJECTS + "/" + recruiting).header("Authorization", admin).contentType("application/json").content("{\"action\":\"DELETE\"}"))
			.andExpect(status().isNoContent());
		assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM projects WHERE id = ?", java.sql.Timestamp.class, recruiting));
		// 지원·팀원 이력은 남는다(주최자 삭제와 같다).
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM project_members WHERE project_id = ?", Integer.class, recruiting));
		mvc.perform(get("/api/v1/projects/" + recruiting)).andExpect(status().isNotFound());
		mvc.perform(get(ADMIN_PROJECTS).header("Authorization", admin)).andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(put(ADMIN_PROJECTS + "/" + running).header("Authorization", admin).contentType("application/json").content("{\"action\":\"DELETE\"}"))
			.andExpect(status().isConflict());
		mvc.perform(put(ADMIN_PROJECTS + "/" + recruiting).header("Authorization", admin).contentType("application/json").content("{\"action\":\"DELETE\"}"))
			.andExpect(status().isNotFound());
		mvc.perform(put(ADMIN_PROJECTS + "/" + running).header("Authorization", admin).contentType("application/json").content("{\"action\":\"HIDE\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(restrict(admin, 999999, true)).andExpect(status().isNotFound());
		mvc.perform(patch(ADMIN_PROJECTS + "/" + running + "/visibility").header("Authorization", admin).contentType("application/json")
			.content("{\"restricted\":\"true\"}")).andExpect(status().isBadRequest());
		assertNull(jdbc.queryForObject("SELECT deleted_at FROM projects WHERE id = ?", java.sql.Timestamp.class, running));
	}
}
