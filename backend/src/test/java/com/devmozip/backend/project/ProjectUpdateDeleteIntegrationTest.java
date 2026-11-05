package com.devmozip.backend.project;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.devmozip.backend.repository.ProjectRepository;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import com.devmozip.backend.service.ProjectApplicationUsage;
import com.devmozip.backend.entity.enums.ProjectJobRole;
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
class ProjectUpdateDeleteIntegrationTest {
	@Container @ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired JdbcTemplate jdbc;
	@Autowired ProjectRepository projects;
	@Autowired JwtProvider jwt;
	@MockitoBean RefreshTokenStore refreshTokens;
	@MockitoBean ProjectCurrentUserProvider user;
	@MockitoBean ProjectApplicationUsage applications;
	@MockitoBean(name = "projectClock") Clock clock;
	private static final String BASE = "/api/v1/projects";
	private long id;
	@BeforeEach void reset() throws Exception {
		jdbc.update("DELETE FROM project_members"); jdbc.update("DELETE FROM projects");
		when(user.getCurrentUserId()).thenReturn(101L);
		when(applications.usedRoles(anyLong())).thenReturn(Set.of());
		when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
		when(clock.instant()).thenReturn(Instant.parse("2026-11-05T01:00:00Z"));
		var result=mvc.perform(post(BASE).header("Authorization",token()).contentType("application/json").content(mapper.writeValueAsString(input())))
			.andExpect(status().isCreated()).andReturn();
		id=mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
		when(clock.instant()).thenReturn(Instant.parse("2026-11-06T01:00:00Z"));
	}
	private String token() { return "Bearer "+jwt.createAccessToken("project-test","USER"); }
	private Map<String,Object> input() {
		Map<String,Object> v=new LinkedHashMap<>();
		v.put("title","기존 공고"); v.put("summary","한 줄 소개"); v.put("description","상세 소개");v.put("goal","목표");
		v.put("imageUrl","https://example.com/cover.png");v.put("isPublic",true);
		v.put("recruitmentSlots",List.of(slot("BACKEND",2),slot("FRONTEND",1)));
		v.put("techStacks",List.of("Java","React"));v.put("interests",List.of("커뮤니티"));
		v.put("recruitmentStart","2026-11-01");v.put("recruitmentEnd","2026-11-20");
		v.put("developmentStart","2026-12-01");v.put("developmentEnd","2027-02-28");return v;
	}
	private Map<String,Object> slot(String role,int capacity) { return Map.of("roleCode",role,"capacity",capacity,"minimumYears",0); }
	private org.springframework.test.web.servlet.ResultActions update(Map<String,Object> body) throws Exception {
		return mvc.perform(put(BASE+"/"+id).header("Authorization",token()).contentType("application/json").content(mapper.writeValueAsString(body)));
	}
	private org.springframework.test.web.servlet.ResultActions remove() throws Exception {
		return mvc.perform(delete(BASE+"/"+id).header("Authorization",token()));
	}
	private void member(long userId,String role,String state) {
		jdbc.update("INSERT INTO project_members(project_id,user_id,role,job_role,status,joined_at,created_at,updated_at) VALUES (?,?,'PARTICIPANT',?,?,current_timestamp,current_timestamp,current_timestamp)",id,userId,role,state);
	}
	@Test void fullReplacementUpdatesJsonbButCannotAssignOwnerStatusOrInternalTimes() throws Exception {
		var body=input();body.put("title"," 새 제목 ");body.put("summary",null);body.put("goal",null);
		body.put("imageUrl","https://example.com/new.png");body.put("isPublic",false);
		body.put("techStacks",List.of(" Svelte "));body.put("interests",List.of("직접 입력"));
		body.put("recruitmentSlots",List.of(slot("FRONTEND",3)));
		body.put("hostMemberId",202);body.put("status","COMPLETED");body.put("deletedAt","2026-01-01T00:00:00");
		update(body).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("새 제목"))
			.andExpect(jsonPath("$.hostMemberId").value(101)).andExpect(jsonPath("$.status").value("RECRUITING"))
			.andExpect(jsonPath("$.techStacks.length()").value(1)).andExpect(jsonPath("$.techStacks[0]").value("Svelte"))
			.andExpect(jsonPath("$.totalCapacity").value(4)).andExpect(jsonPath("$.isPublic").value(false))
			.andExpect(jsonPath("$.createdAt").value("2026-11-05T10:00:00"))
			.andExpect(jsonPath("$.updatedAt").value("2026-11-06T10:00:00"));
		var p=projects.findById(id).orElseThrow();assertNull(p.getGoal());assertNull(p.getSummary());assertNull(p.getDeletedAt());
		assertEquals(List.of("직접 입력"),p.getInterests());assertEquals(1,p.getRecruitmentSlots().size());
		assertEquals("FRONTEND",jdbc.queryForObject("SELECT recruitment_slots->0->>'roleCode' FROM projects WHERE id=?",String.class,id));
	}
	@ParameterizedTest @ValueSource(strings={"title","description","imageUrl","isPublic","recruitmentSlots","techStacks","interests","recruitmentStart","recruitmentEnd","developmentStart","developmentEnd"})
	void omittedRequiredFieldDoesNotBehaveLikePatch(String field) throws Exception {
		var body=input();body.remove(field);update(body).andExpect(status().isBadRequest());assertEquals("기존 공고",projects.findById(id).orElseThrow().getTitle());
	}
	@Test void validatesCustomNamesDuplicatesImagesSlotsAndPreservesOldValuesOnFailure() throws Exception {
		for(var change:List.of(Map.of("imageUrl",""),Map.of("imageUrl","javascript:alert(1)"),Map.of("techStacks",List.of("a","b","c","d","e","f")),
			Map.of("interests",List.of("ＡＩ","AI")),Map.of("recruitmentSlots",List.of(slot("BACKEND",1),slot("BACKEND",2))))) {
			var body=input();body.put("title","저장되면 안 됨");body.putAll(change);update(body).andExpect(status().isBadRequest());
			assertEquals("기존 공고",projects.findById(id).orElseThrow().getTitle());
		}
	}
	@Test void nonOwnersAnonymousMissingAndDeletedTargetsAreRejected() throws Exception {
		mvc.perform(put(BASE+"/"+id).contentType("application/json").content(mapper.writeValueAsString(input()))).andExpect(status().isUnauthorized());
		mvc.perform(delete(BASE+"/"+id)).andExpect(status().isUnauthorized());
		when(user.getCurrentUserId()).thenReturn(202L);update(input()).andExpect(status().isForbidden());remove().andExpect(status().isForbidden());
		when(user.getCurrentUserId()).thenReturn(101L);long keep=id;id=999999;update(input()).andExpect(status().isNotFound());remove().andExpect(status().isNotFound());
		id=keep;remove().andExpect(status().isNoContent());update(input()).andExpect(status().isNotFound());remove().andExpect(status().isNotFound());
	}
	@Test void inactiveOwnerCannotMutateProject() throws Exception {
		jdbc.update("UPDATE project_members SET status='LEFT' WHERE project_id=? AND user_id=101",id);
		update(input()).andExpect(status().isForbidden());remove().andExpect(status().isForbidden());
	}
	@Test void completedCannotBeEditedAndNonRecruitingCannotBeDeleted() throws Exception {
		for(String state:List.of("RECRUITMENT_COMPLETED","IN_PROGRESS","COMPLETED")) {
			jdbc.update("UPDATE projects SET status=? WHERE id=?",state,id);remove().andExpect(status().isConflict());
		}
		update(input()).andExpect(status().isConflict());assertNull(projects.findById(id).orElseThrow().getDeletedAt());
	}
	@Test void pastDeadlineDoesNotBlockContentChangesAndEndDatesOnlyExtend() throws Exception {
		jdbc.update("UPDATE projects SET recruitment_end=DATE '2026-11-02' WHERE id=?",id);
		var body=input();body.put("recruitmentEnd","2026-11-02");body.put("title","마감 후 소개 변경");
		update(body).andExpect(status().isOk());
		body.put("recruitmentEnd","2026-11-01");update(body).andExpect(status().isConflict());
		body.put("recruitmentEnd","2026-11-30");body.put("developmentEnd","2027-03-01");update(body).andExpect(status().isOk());
		body.put("developmentEnd","2027-02-28");update(body).andExpect(status().isConflict());
		body.put("developmentEnd","2027-03-01");body.put("recruitmentStart","2026-12-05");update(body).andExpect(status().isBadRequest());
	}
	@Test void inProgressStartDatesCannotChangeButDescriptionCan() throws Exception {
		jdbc.update("UPDATE projects SET status='IN_PROGRESS' WHERE id=?",id);
		var body=input();body.put("title","진행 중 설명");update(body).andExpect(status().isOk());
		body.put("developmentStart","2026-12-02");update(body).andExpect(status().isConflict());
	}
	@Test void usedMemberRoleCannotBeRemovedAndCapacityCannotShrinkBelowActiveMembers() throws Exception {
		member(202,"BACKEND","ACTIVE");member(303,"BACKEND","ACTIVE");member(404,"FRONTEND","ACTIVE");member(505,"BACKEND","LEFT");
		var body=input();body.put("recruitmentSlots",List.of(slot("BACKEND",2)));update(body).andExpect(status().isConflict());
		body.put("recruitmentSlots",List.of(slot("BACKEND",1),slot("FRONTEND",1)));update(body).andExpect(status().isConflict());
		body.put("recruitmentSlots",List.of(slot("BACKEND",2),slot("FRONTEND",1)));update(body).andExpect(status().isOk());
	}
	@Test void applicationUsagePortProtectsRolesWithoutImplementingApplicationTable() throws Exception {
		when(applications.usedRoles(id)).thenReturn(Set.of(ProjectJobRole.BACKEND));
		var body=input();body.put("recruitmentSlots",List.of(slot("FRONTEND",1)));update(body).andExpect(status().isConflict());
		assertEquals("기존 공고",projects.findById(id).orElseThrow().getTitle());
	}
	@Test void recruitingDeleteRetainsMembersAndHistoryReferences() throws Exception {
		member(202,"BACKEND","ACTIVE");when(applications.usedRoles(id)).thenReturn(Set.of(ProjectJobRole.BACKEND));
		remove().andExpect(status().isNoContent());
		var p=projects.findById(id).orElseThrow();assertNotNull(p.getDeletedAt());assertEquals("기존 공고",p.getTitle());
		assertEquals(LocalDateTime.of(2026,11,6,10,0),p.getDeletedAt());assertEquals(p.getDeletedAt(),p.getUpdatedAt());
		assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM project_members WHERE project_id=?",Integer.class,id));
		assertTrue(projects.findByIdAndDeletedAtIsNull(id).isEmpty());
	}
	@Test void databaseFailureRollsBackAllFieldChangesAndDeletion() throws Exception {
		jdbc.execute("ALTER TABLE projects ADD CONSTRAINT update_test_guard CHECK (title <> 'BLOCKED')");
		jdbc.execute("ALTER TABLE projects ADD CONSTRAINT delete_test_guard CHECK (deleted_at IS NULL)");
		try {
			var body=input();body.put("title","BLOCKED");body.put("techStacks",List.of("Svelte"));update(body).andExpect(status().isInternalServerError());
			var p=projects.findById(id).orElseThrow();assertEquals("기존 공고",p.getTitle());assertEquals(List.of("Java","React"),p.getTechStacks());
			remove().andExpect(status().isInternalServerError());assertNull(projects.findById(id).orElseThrow().getDeletedAt());
		} finally { jdbc.execute("ALTER TABLE projects DROP CONSTRAINT update_test_guard");jdbc.execute("ALTER TABLE projects DROP CONSTRAINT delete_test_guard"); }
	}
	@Test void updateAndQueryIntegrationPreservesViewCountAndNewFields() throws Exception {
		mvc.perform(get(BASE+"/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.project.viewCount").value(1));
		var body=input();body.put("title","통합 수정 공고");body.put("techStacks",List.of("Svelte"));
		update(body).andExpect(status().isOk()).andExpect(jsonPath("$.viewCount").value(1));
		mvc.perform(get(BASE).param("query","Svelte")).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].title").value("통합 수정 공고"));
		mvc.perform(get(BASE+"/"+id)).andExpect(status().isOk())
			.andExpect(jsonPath("$.project.viewCount").value(2)).andExpect(jsonPath("$.project.techStacks[0]").value("Svelte"));
	}

	@Test void deletedProjectIsHiddenFromEveryQueryButMembersRemain() throws Exception {
		remove().andExpect(status().isNoContent());
		mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get(BASE+"/"+id)).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/me/projects").header("Authorization",token()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get(BASE+"/"+id+"/manage").header("Authorization",token())).andExpect(status().isNotFound());
		assertTrue(projects.findById(id).isPresent());
		assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM project_members WHERE project_id=?",Integer.class,id));
	}

	@Test void concurrentUpdateAndDeleteNeverResurrectDeletedProject() throws Exception {
		try(var executor=Executors.newFixedThreadPool(2)) {
			var body=mapper.writeValueAsString(input());
			var tasks=List.<Callable<Integer>>of(
				()->mvc.perform(put(BASE+"/"+id).header("Authorization",token()).contentType("application/json").content(body)).andReturn().getResponse().getStatus(),
				()->mvc.perform(delete(BASE+"/"+id).header("Authorization",token())).andReturn().getResponse().getStatus());
			var results=executor.invokeAll(tasks);int updateStatus=results.get(0).get(20,TimeUnit.SECONDS);
			assertTrue(updateStatus==200||updateStatus==404);assertEquals(204,results.get(1).get(20,TimeUnit.SECONDS));
		}
		assertNotNull(projects.findById(id).orElseThrow().getDeletedAt());
	}
}
