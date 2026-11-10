package com.devmozip.backend.project;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.entity.ProjectMember;
import com.devmozip.backend.entity.ProjectRecruitmentSlot;
import com.devmozip.backend.repository.ProjectRepository;
import com.devmozip.backend.repository.ProjectMemberRepository;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ProjectQueryIntegrationTest {
	@MockitoBean com.devmozip.backend.service.ProjectCreationPolicy creationPolicy;
	@Container @ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;
	@Autowired ProjectRepository projects;
	@Autowired ProjectMemberRepository members;
	@MockitoBean RefreshTokenStore refreshTokens;
	@MockitoBean ProjectCurrentUserProvider currentUser;
	@MockitoBean(name = "projectClock") Clock clock;
	private static final String BASE = "/api/v1/projects";
	private long first, second, privateId, deleted;
	private final LocalDate today = LocalDate.of(2026,11,4);
	private final LocalDateTime now = LocalDateTime.of(2026,11,4,1,0);

	@BeforeEach void reset() {
		jdbc.update("DELETE FROM review_keyword_selections"); jdbc.update("DELETE FROM reviews"); jdbc.update("DELETE FROM project_members"); jdbc.update("DELETE FROM projects");
		when(currentUser.getCurrentUserId()).thenReturn(101L);
		when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
		when(clock.instant()).thenReturn(Instant.parse("2026-11-03T16:00:00Z"));
		first = create("Alpha", true, 101, List.of("React", "Java"), List.of("교육"), 1);
		second = create("Beta", true, 202, List.of("React"), List.of("게임"), 0);
		privateId = create("Private", false, 101, List.of("React", "Java"), List.of("교육"), 2);
		deleted = create("Deleted", true, 101, List.of("React"), List.of("교육"), 3);
		jdbc.update("UPDATE projects SET deleted_at=current_timestamp WHERE id=?", deleted);
	}
	private long create(String title, boolean visible, long host, List<String> stacks, List<String> interests, int endsInDays) {
		Project p = projects.saveAndFlush(new Project(host,title,"요약","상세 소개",null,"https://example.com/a.png",visible,
			List.of(new ProjectRecruitmentSlot("BACKEND",2,0)),stacks,interests,
			today.minusDays(2),today.plusDays(endsInDays),today,today.plusMonths(2),now));
		members.saveAndFlush(ProjectMember.host(p,host,now));
		return p.getId();
	}
	private String token() { return "Bearer " + jwt.createAccessToken("query-test", "USER"); }
	private void participant(long id, long userId, String status) {
		jdbc.update("INSERT INTO project_members(project_id,user_id,role,job_role,status,joined_at,created_at,updated_at) VALUES (?,?,'PARTICIPANT','BACKEND',?,?,?,?)", id,userId,status,now,now,now);
	}
	@Test void publicProfileActivitiesExcludePrivateDeletedAndApplications() throws Exception {
		jdbc.update("INSERT INTO users(id,email,tag,role,status) VALUES (101,'activity@project.test','activity-host','USER','ACTIVE') ON CONFLICT(id) DO UPDATE SET tag='activity-host',status='ACTIVE'");
		participant(second,101,"LEFT");
		mvc.perform(get("/api/v1/users/activity-host/projects"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.items[0].projectId").value(second))
			.andExpect(jsonPath("$.items[0].role").value("PARTICIPANT"))
			.andExpect(jsonPath("$.items[0].membershipStatus").value("LEFT"))
			.andExpect(jsonPath("$.items[1].role").value("HOST"));
	}
	@Test void ownActivitiesUseAuthenticatedIdentityAndPaginateWithoutRecordingViews() throws Exception {
		mvc.perform(get("/api/v1/users/me/projects")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/users/me/projects").header("Authorization",token()).param("size","1").param("page","1").param("userId","202"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items[0].projectId").value(first));
		assertEquals(0,projects.findById(first).orElseThrow().getViewCount());
	}
	@Test void privateActivitiesRequireCurrentViewerMembership() throws Exception {
		jdbc.update("INSERT INTO users(id,email,tag,role,status) VALUES (101,'activity@project.test','activity-host','USER','ACTIVE') ON CONFLICT(id) DO UPDATE SET tag='activity-host',status='ACTIVE'");
		when(currentUser.getCurrentUserId()).thenReturn(202L);
		mvc.perform(get("/api/v1/users/activity-host/projects").header("Authorization",token()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
		participant(privateId,202,"ACTIVE");
		mvc.perform(get("/api/v1/users/activity-host/projects").header("Authorization",token()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
		jdbc.update("UPDATE project_members SET status='LEFT' WHERE project_id=? AND user_id=202",privateId);
		mvc.perform(get("/api/v1/users/activity-host/projects").header("Authorization",token()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
	}
	@Test void activitiesRejectUnknownInactiveTargetsAndInvalidPages() throws Exception {
		mvc.perform(get("/api/v1/users/unknown-profile/projects")).andExpect(status().isNotFound());
		jdbc.update("INSERT INTO users(id,email,tag,role,status) VALUES (101,'activity@project.test','activity-host','USER','SUSPENDED') ON CONFLICT(id) DO UPDATE SET tag='activity-host',status='SUSPENDED'");
		mvc.perform(get("/api/v1/users/activity-host/projects")).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/users/me/projects").header("Authorization",token()).param("page","-1")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/users/me/projects").header("Authorization",token()).param("size","51")).andExpect(status().isBadRequest());
	}

	@Test void anonymousListHasOnlyPublicNonDeletedAndStablePagination() throws Exception {
		mvc.perform(get(BASE).param("size","1"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items[0].id").value(second))
			.andExpect(jsonPath("$.items[0].memberCount").value(1))
			.andExpect(jsonPath("$.items[0].totalCapacity").value(3));
		mvc.perform(get(BASE).param("size","1").param("page","1"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(first));
		mvc.perform(get(BASE).param("page","100"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(2));
	}
	@Test void jsonbInterestsAreOrAndStacksAreAndWithCaseInsensitiveSearch() throws Exception {
		mvc.perform(get(BASE).param("interests","교육","게임").param("techStacks","react","JAVA").param("jobRole","BACKEND"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(first));
		mvc.perform(get(BASE).param("query","rEaCt"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
		mvc.perform(get(BASE).param("query","alpha"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(first));
		mvc.perform(get(BASE).param("query","%"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
		mvc.perform(get(BASE).param("query","' OR 1=1 --"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
		mvc.perform(get(BASE).param("jobRole","FRONTEND"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalPages").value(0));
	}
	@Test void deadlineSortIncludesTodayButExcludesExpiredAndNonRecruiting() throws Exception {
		mvc.perform(get(BASE).param("sort","DEADLINE"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(second))
			.andExpect(jsonPath("$.items[0].recruitmentClosed").value(false));
		jdbc.update("UPDATE projects SET recruitment_end=? WHERE id=?", today.minusDays(1), second);
		mvc.perform(get(BASE).param("sort","DEADLINE"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(get(BASE+"/"+second)).andExpect(status().isOk()).andExpect(jsonPath("$.recruitmentClosed").value(true));
		jdbc.update("UPDATE projects SET status='IN_PROGRESS' WHERE id=?", first);
		mvc.perform(get(BASE).param("sort","DEADLINE")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
		mvc.perform(get(BASE).param("status","IN_PROGRESS")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(first));
	}
	@Test void onlyActiveTeamCanReadPrivateAndDeniedReadsDoNotIncreaseViews() throws Exception {
		mvc.perform(get(BASE+"/"+privateId)).andExpect(status().isNotFound());
		mvc.perform(get(BASE+"/"+privateId).header("Authorization",token())).andExpect(status().isOk());
		when(currentUser.getCurrentUserId()).thenReturn(303L);
		mvc.perform(get(BASE+"/"+privateId).header("Authorization",token())).andExpect(status().isNotFound());
		participant(privateId,303,"LEFT");
		mvc.perform(get(BASE+"/"+privateId).header("Authorization",token())).andExpect(status().isNotFound());
		jdbc.update("UPDATE project_members SET status='ACTIVE' WHERE project_id=? AND user_id=303",privateId);
		mvc.perform(get(BASE+"/"+privateId).header("Authorization",token())).andExpect(status().isOk())
			.andExpect(jsonPath("$.memberCount").value(2)).andExpect(jsonPath("$.project.viewCount").value(2));
		mvc.perform(get(BASE+"/"+deleted)).andExpect(status().isNotFound());
		mvc.perform(get(BASE+"/999999")).andExpect(status().isNotFound());
		assertEquals(0L, projects.findById(deleted).orElseThrow().getViewCount());
	}
	@Test void everyPublicDetailCountsAndPopularityUsesViewsThenLatestId() throws Exception {
		mvc.perform(get(BASE+"/"+first)).andExpect(status().isOk()).andExpect(jsonPath("$.project.viewCount").value(1));
		mvc.perform(get(BASE+"/"+first)).andExpect(status().isOk()).andExpect(jsonPath("$.project.viewCount").value(2));
		mvc.perform(get(BASE).param("sort","POPULAR")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(first));
		assertEquals(2L, projects.findById(first).orElseThrow().getViewCount());
	}
	@Test void concurrentDetailReadsDoNotLoseCounts() throws Exception {
		try(var executor = Executors.newFixedThreadPool(4)) {
			List<Callable<Void>> calls = new ArrayList<>();
			for (int i=0;i<8;i++) calls.add(() -> { mvc.perform(get(BASE+"/"+first)).andExpect(status().isOk()); return null; });
			for (var future : executor.invokeAll(calls)) future.get(20,TimeUnit.SECONDS);
		}
		assertEquals(8L, projects.findById(first).orElseThrow().getViewCount());
	}
	@Test void ownTabsAndManagementDoNotExposeOthersOrIncrementViews() throws Exception {
		participant(second,101,"ACTIVE");
		mvc.perform(get("/api/v1/me/projects")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/me/projects").header("Authorization",token())).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3));
		mvc.perform(get("/api/v1/me/projects").header("Authorization",token()).param("tab","PARTICIPATING"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(second))
			.andExpect(jsonPath("$.items[0].myRole").value("PARTICIPANT"));
		mvc.perform(get("/api/v1/me/projects").header("Authorization",token()).param("tab","HOSTED"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
		jdbc.update("UPDATE projects SET status='COMPLETED' WHERE id=?", second);
		mvc.perform(get("/api/v1/me/projects").header("Authorization",token()).param("tab","COMPLETED"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(second));
		mvc.perform(get("/api/v1/me/projects").header("Authorization",token()).param("tab","APPLYING"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get(BASE+"/"+first+"/manage").header("Authorization",token())).andExpect(status().isOk());
		mvc.perform(get(BASE+"/"+second+"/manage").header("Authorization",token())).andExpect(status().isForbidden());
		mvc.perform(get(BASE+"/"+privateId+"/manage")).andExpect(status().isUnauthorized());
		assertEquals(0L, projects.findById(first).orElseThrow().getViewCount());
	}
	@Test void invalidFiltersReturn400AndPrivateCountsNeverLeakIntoSearch() throws Exception {
		for (var pair : List.of(new String[]{"page","-1"},new String[]{"size","0"},new String[]{"size","101"},
			new String[]{"sort","UNKNOWN"},new String[]{"jobRole","x".repeat(31)},new String[]{"status","UNKNOWN"},new String[]{"query","x".repeat(256)}))
			mvc.perform(get(BASE).param(pair[0],pair[1])).andExpect(status().isBadRequest());
		mvc.perform(get(BASE).param("interests","a","b","c","d","e","f")).andExpect(status().isBadRequest());
		mvc.perform(get(BASE).param("query","Private").header("Authorization",token()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get(BASE+"/options")).andExpect(status().isUnauthorized());
	}
}
