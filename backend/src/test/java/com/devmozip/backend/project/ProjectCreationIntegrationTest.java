package com.devmozip.backend.project;

import java.time.*;
import java.util.*;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ProjectCreationIntegrationTest {
	@Container @ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired com.devmozip.backend.repository.ProjectRepository projects;
	@Autowired ObjectMapper mapper;
	@Autowired JwtProvider jwt;
	@MockitoBean RefreshTokenStore refreshTokens;
	@MockitoBean ProjectCurrentUserProvider currentUser;
	@MockitoBean(name = "projectClock") Clock clock;
	private static final String BASE = "/api/v1/projects";

	@BeforeEach void reset() {
		jdbc.update("DELETE FROM project_members");
		jdbc.update("DELETE FROM projects");
		when(currentUser.getCurrentUserId()).thenReturn(101L);
		when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
		// UTC 11/3이지만 서울은 11/4. 오늘 마감은 허용한다.
		when(clock.instant()).thenReturn(Instant.parse("2026-11-03T16:00:00Z"));
	}
	private String token() { return "Bearer " + jwt.createAccessToken("project-test", "USER"); }
	private Map<String, Object> valid() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", " 프로젝트 ");
		body.put("summary", "한 줄 소개");
		body.put("description", "프로젝트 설명");
		body.put("imageUrl", "https://example.com/project.webp");
		body.put("isPublic", false);
		body.put("recruitmentSlots", List.of(Map.of("roleCode", "FRONTEND", "capacity", 2, "minimumYears", 0),
			Map.of("roleCode", "BACKEND", "capacity", 1, "minimumYears", 1)));
		body.put("techStacks", List.of(" React ", "Svelte"));
		body.put("interests", List.of("커뮤니티", "직접 입력 분야"));
		body.put("recruitmentStart", "2026-11-01");
		body.put("recruitmentEnd", "2026-11-04");
		body.put("developmentStart", "2026-11-04");
		body.put("developmentEnd", "2027-02-28");
		return body;
	}
	private org.springframework.test.web.servlet.ResultActions create(Map<String,Object> body) throws Exception {
		return mvc.perform(post(BASE).header("Authorization", token()).contentType("application/json")
			.content(mapper.writeValueAsString(body)));
	}
	@Test void createsJsonbAndHostAtomicallyWithoutTakingAJobSlot() throws Exception {
		var body = valid();
		body.put("hostMemberId", 999); // 요청 ID는 공급 경계를 바꾸지 않는다.
		body.put("status", "COMPLETED");
		var result = create(body).andExpect(status().isCreated())
			.andExpect(jsonPath("$.hostMemberId").value(101))
			.andExpect(jsonPath("$.title").value("프로젝트"))
			.andExpect(jsonPath("$.status").value("RECRUITING"))
			.andExpect(jsonPath("$.isPublic").value(false))
			.andExpect(jsonPath("$.techStacks[0]").value("React"))
			.andExpect(jsonPath("$.totalCapacity").value(4))
			.andExpect(jsonPath("$.createdAt").value("2026-11-04T01:00:00"))
			.andReturn();
		long id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
		assertEquals(BASE + "/" + id, result.getResponse().getHeader("Location"));
		var restored = projects.findById(id).orElseThrow();
		assertEquals(com.devmozip.backend.entity.enums.ProjectJobRole.FRONTEND, restored.getRecruitmentSlots().getFirst().roleCode());
		assertEquals(List.of("React", "Svelte"), restored.getTechStacks());
		assertEquals(4, restored.getTotalCapacity());
		assertEquals("jsonb", jdbc.queryForObject("SELECT pg_typeof(recruitment_slots)::text FROM projects WHERE id=?", String.class, id));
		assertEquals(2, jdbc.queryForObject("SELECT jsonb_array_length(recruitment_slots) FROM projects WHERE id=?", Integer.class, id));
		assertEquals("FRONTEND", jdbc.queryForObject("SELECT recruitment_slots->0->>'roleCode' FROM projects WHERE id=?", String.class, id));
		assertEquals("Svelte", jdbc.queryForObject("SELECT tech_stacks->>1 FROM projects WHERE id=?", String.class, id));
		assertEquals("직접 입력 분야", jdbc.queryForObject("SELECT interests->>1 FROM projects WHERE id=?", String.class, id));
		var host = jdbc.queryForMap("SELECT * FROM project_members WHERE project_id=?", id);
		assertEquals(101L, ((Number)host.get("user_id")).longValue());
		assertEquals("HOST", host.get("role"));
		assertEquals("ACTIVE", host.get("status"));
		assertNull(host.get("job_role"));
		assertNull(host.get("end_requested_at"));
	}
	@Test void optionsProvideCodesAndLabelsWhileAnonymousRequestsRemainProtected() throws Exception {
		mvc.perform(get(BASE + "/options")).andExpect(status().isUnauthorized());
		mvc.perform(post(BASE).contentType("application/json").content(mapper.writeValueAsString(valid())))
			.andExpect(status().isUnauthorized());
		mvc.perform(get(BASE + "/options").header("Authorization", token()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.jobRoles[0].code").value("FRONTEND"))
			.andExpect(jsonPath("$.techStacks[0]").value("React"));
	}
	@ParameterizedTest @ValueSource(strings = {"title", "description", "imageUrl", "isPublic", "recruitmentSlots", "techStacks", "interests", "recruitmentStart", "recruitmentEnd", "developmentStart", "developmentEnd"})
	void missingRequiredValuesRejectWithoutWrites(String field) throws Exception {
		var body = valid(); body.remove(field);
		create(body).andExpect(status().isBadRequest()); assertEmpty();
	}
	@Test void rejectsDuplicatesUnknownRolesAndInvalidNumbers() throws Exception {
		for (var slots : List.of(
			List.of(Map.of("roleCode", "UNKNOWN", "capacity", 1, "minimumYears", 0)),
			List.of(Map.of("roleCode", "BACKEND", "capacity", 0, "minimumYears", 0)),
			List.of(Map.of("roleCode", "BACKEND", "capacity", 11, "minimumYears", 0)),
			List.of(Map.of("roleCode", "BACKEND", "capacity", 1, "minimumYears", 6)),
			List.of(Map.of("roleCode", "BACKEND", "capacity", 1, "minimumYears", 0), Map.of("roleCode", "BACKEND", "capacity", 2, "minimumYears", 1)))) {
			var body = valid(); body.put("recruitmentSlots", slots); create(body).andExpect(status().isBadRequest());
		}
		for (String field : List.of("techStacks", "interests")) {
			for (var values : List.of(List.of(" React ", "Ｒｅａｃｔ"), List.of("a", "b", "c", "d", "e", "f"), List.of(" "), List.of("a".repeat(31)))) {
				var body = valid(); body.put(field, values); create(body).andExpect(status().isBadRequest());
			}
		}
		assertEmpty();
	}
	@Test void rejectsInvalidDatesAndImageUrls() throws Exception {
		for (var change : List.of(Map.of("recruitmentEnd", "2026-11-03"), Map.of("recruitmentStart", "2026-11-05"),
			Map.of("developmentStart", "2027-03-01"), Map.of("developmentEnd", "2026-10-31"),
			Map.of("recruitmentStart", "invalid"), Map.of("imageUrl", "javascript:alert(1)"),
			Map.of("imageUrl", "https://"), Map.of("imageUrl", "https://u:p@example.com/a"), Map.of("imageUrl", "a".repeat(2049)))) {
			var body = valid(); body.putAll(change); create(body).andExpect(status().isBadRequest());
		}
		assertEmpty();
	}
	@Test void rejectsNullNestedValuesAndKeepsOptionalSelectionsEmpty() throws Exception {
		for (String field : List.of("isPublic", "recruitmentSlots", "techStacks", "interests")) {
			var body = valid(); body.put(field, null); create(body).andExpect(status().isBadRequest());
		}
		for (String field : List.of("recruitmentSlots", "techStacks", "interests")) {
			var body = valid(); body.put(field, Arrays.asList((Object)null)); create(body).andExpect(status().isBadRequest());
		}
		var body = valid(); body.put("techStacks", List.of()); body.put("interests", List.of());
		create(body).andExpect(status().isCreated()).andExpect(jsonPath("$.techStacks").isEmpty())
			.andExpect(jsonPath("$.interests").isEmpty());
	}
	@Test void hostInsertFailureRollsBackProject() throws Exception {
		jdbc.execute("ALTER TABLE project_members ADD CONSTRAINT project_test_block_host CHECK (user_id <> 101)");
		try {
			create(valid()).andExpect(status().isInternalServerError());
			assertEmpty();
		} finally { jdbc.execute("ALTER TABLE project_members DROP CONSTRAINT project_test_block_host"); }
	}
	private void assertEmpty() {
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM projects", Integer.class));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM project_members", Integer.class));
	}
}
