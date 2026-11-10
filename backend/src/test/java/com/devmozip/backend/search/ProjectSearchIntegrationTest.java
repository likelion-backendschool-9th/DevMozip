package com.devmozip.backend.search;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.entity.ProjectMember;
import com.devmozip.backend.entity.ProjectRecruitmentSlot;
import com.devmozip.backend.repository.ProjectMemberRepository;
import com.devmozip.backend.repository.ProjectRepository;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * GET /api/v1/search?type=projects (WBS 6.3·6.10). 프로젝트 탐색 전용 조회가 7.1 목록과 같은 공개 범위를 쓰는지도 확인한다.
 * 테이블과 데이터는 이 테스트 전용 PostgreSQL 컨테이너에만 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ProjectSearchIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@MockitoBean(name = "searchClock") Clock clock;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ProjectRepository projects;
	@Autowired ProjectMemberRepository members;

	static final String SEARCH = "/api/v1/search";
	// 2026-11-04 01:00 (Asia/Seoul)
	static final Instant NOW = Instant.parse("2026-11-03T16:00:00Z");
	static final LocalDate TODAY = LocalDate.of(2026, 11, 4);
	static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 1, 9, 0, 30);

	long alpha, beta, gamma, delta, epsilon, hidden, removed;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.sql.init.mode", () -> "never");
	}

	@BeforeEach
	void fixtures() {
		jdbc.update("DELETE FROM project_members");
		jdbc.update("DELETE FROM projects");
		at(NOW);
		alpha = create("Alpha Fintech", true, List.of(slot("BACKEND", 2), slot("FRONTEND", 1), slot("BACKEND", 1)),
			List.of("Spring Boot", "React"), List.of("핀테크"), TODAY, 0);
		beta = create("Beta 교육", true, List.of(slot("보안 엔지니어", 1)), List.of("Python"), List.of("교육"), TODAY.minusDays(1), 1);
		gamma = create("Gamma", true, List.of(slot("DESIGNER", 1)), List.of("Figma", "React"), List.of("AI"), TODAY.plusDays(5), 2);
		delta = create("Delta", true, List.of(slot("AI_ENGINEER", 2)), List.of("Python"), List.of("AI", "교육"), TODAY.plusDays(5), 3);
		// delta와 작성 시각이 같으면 id가 큰 공고가 먼저다.
		epsilon = create("Epsilon", true, List.of(slot("DEVOPS", 1)), List.of(), List.of("커뮤니티"), TODAY.plusDays(5), 3);
		hidden = create("Hidden Fintech", false, List.of(slot("BACKEND", 1)), List.of("React"), List.of("핀테크"), TODAY.plusDays(5), 4);
		removed = create("Removed Fintech", true, List.of(slot("BACKEND", 1)), List.of("React"), List.of("핀테크"), TODAY.plusDays(5), 5);
		jdbc.update("UPDATE projects SET deleted_at = now() WHERE id = ?", removed);
		jdbc.update("UPDATE projects SET status = 'RECRUITMENT_COMPLETED' WHERE id = ?", gamma);
		jdbc.update("UPDATE projects SET status = 'IN_PROGRESS' WHERE id = ?", delta);
		jdbc.update("UPDATE projects SET status = 'COMPLETED' WHERE id = ?", epsilon);
		// 참여 인원은 ACTIVE만 센다: alpha는 주최자 + 참여자 1명
		participant(alpha, 301, "ACTIVE");
		participant(alpha, 302, "LEFT");
	}

	void at(Instant instant) {
		when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
		when(clock.instant()).thenReturn(instant);
	}

	static ProjectRecruitmentSlot slot(String role, int capacity) {
		return new ProjectRecruitmentSlot(role, capacity, 0);
	}

	long create(String title, boolean visible, List<ProjectRecruitmentSlot> slots, List<String> stacks, List<String> interests,
		LocalDate recruitmentEnd, int createdDays) {
		LocalDateTime created = CREATED.plusDays(createdDays);
		Project project = projects.saveAndFlush(new Project(100, title, title + " 요약", "상세", null, "https://example.com/p.png",
			visible, slots, stacks, interests, TODAY.minusDays(10), recruitmentEnd, TODAY.plusDays(10), TODAY.plusMonths(3), created));
		members.saveAndFlush(ProjectMember.host(project, 100, created));
		return project.getId();
	}

	void participant(long projectId, long userId, String status) {
		jdbc.update("INSERT INTO project_members(project_id, user_id, role, job_role, status, joined_at, created_at, updated_at)"
			+ " VALUES (?, ?, 'PARTICIPANT', 'BACKEND', ?, now(), now(), now())", projectId, userId, status);
	}

	MockHttpServletRequestBuilder search() {
		return get(SEARCH).param("type", "projects");
	}

	ResultActions ids(MockHttpServletRequestBuilder request, Long... expected) throws Exception {
		return mvc.perform(request).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCount").value(expected.length))
			.andExpect(jsonPath("$.items[*].id", containsInAnyOrder(java.util.Arrays.stream(expected).map(Long::intValue).toArray())));
	}

	@Test
	void anonymousSearchListsPublicLiveProjectsNewestFirst() throws Exception {
		mvc.perform(search())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCount").value(5))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.items[*].id", contains((int) epsilon, (int) delta, (int) gamma, (int) beta, (int) alpha)))
			.andExpect(jsonPath("$.items[4].title").value("Alpha Fintech"))
			.andExpect(jsonPath("$.items[4].summary").value("Alpha Fintech 요약"))
			.andExpect(jsonPath("$.items[4].imageUrl").value("https://example.com/p.png"))
			.andExpect(jsonPath("$.items[4].status").value("RECRUITING"))
			.andExpect(jsonPath("$.items[4].jobRoles", contains("BACKEND", "FRONTEND")))
			.andExpect(jsonPath("$.items[4].techStacks", contains("Spring Boot", "React")))
			.andExpect(jsonPath("$.items[4].interests", contains("핀테크")))
			.andExpect(jsonPath("$.items[4].totalCapacity").value(5))
			.andExpect(jsonPath("$.items[4].memberCount").value(2))
			.andExpect(jsonPath("$.items[4].developmentStart").value("2026-11-14"))
			.andExpect(jsonPath("$.items[4].developmentEnd").value("2027-02-04"))
			.andExpect(jsonPath("$.items[4].createdAt").value("2026-10-01T09:00:30"))
			.andExpect(jsonPath("$.items[4].hostMemberId").doesNotExist())
			.andExpect(jsonPath("$.items[4].viewCount").doesNotExist())
			.andExpect(jsonPath("$.items[4].bookmarked").doesNotExist())
			.andExpect(jsonPath("$.items[3].status").value("RECRUITMENT_CLOSED"))
			.andExpect(jsonPath("$.items[3].jobRoles", contains("보안 엔지니어")))
			.andExpect(jsonPath("$.items[3].memberCount").value(1));
	}

	@Test
	void visibilityMatchesProjectListOfSameData() throws Exception {
		String search = mvc.perform(search().param("size", "100")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String list = mvc.perform(get("/api/v1/projects").param("size", "100")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<Integer> searched = JsonPath.read(search, "$.items[*].id");
		List<Integer> listed = JsonPath.read(list, "$.items[*].id");
		assertEquals(new HashSet<>(listed), new HashSet<>(searched));
		assertEquals(5, searched.size());
	}

	@Test
	void recruitmentStatusUsesTodayInclusiveDeadline() throws Exception {
		ids(search().param("status", "RECRUITING"), alpha);
		ids(search().param("status", "RECRUITMENT_CLOSED"), beta);
		ids(search().param("status", "RECRUITMENT_COMPLETED"), gamma);
		ids(search().param("status", "IN_PROGRESS"), delta);
		ids(search().param("status", "COMPLETED"), epsilon);

		// 다음 날(2026-11-05)이 되면 마감일이 오늘이던 공고도 모집 마감이다.
		at(Instant.parse("2026-11-04T15:00:00Z"));
		ids(search().param("status", "RECRUITING"));
		ids(search().param("status", "RECRUITMENT_CLOSED"), alpha, beta);
		mvc.perform(search().param("q", "alpha")).andExpect(jsonPath("$.items[0].status").value("RECRUITMENT_CLOSED"));
		// 바로 전 시각(2026-11-04 23:59:59)까지는 모집 중이다.
		at(Instant.parse("2026-11-04T14:59:59Z"));
		ids(search().param("status", "RECRUITING"), alpha);
	}

	@Test
	void everyKeywordWordMustMatchTitleFieldRoleOrStack() throws Exception {
		ids(search().param("q", "alpha"), alpha);
		ids(search().param("q", "FINTECH"), alpha);
		// 기본 직군은 라벨로 찾고, 코드 글자로는 찾지 않는다.
		ids(search().param("q", "백엔드"), alpha);
		ids(search().param("q", "프론트"), alpha);
		ids(search().param("q", "ui/ux"), gamma);
		ids(search().param("q", "dev"), epsilon);
		ids(search().param("q", "end"));
		ids(search().param("q", "backend"));
		// 직접 입력한 직군은 저장된 문구로 찾는다.
		ids(search().param("q", "보안"), beta);
		ids(search().param("q", "python 교육"), beta, delta);
		ids(search().param("q", "react 핀테크"), alpha);
		ids(search().param("q", "ＲＥＡＣＴ"), alpha, gamma);
		ids(search().param("q", "%"));
		ids(search().param("q", "_"));
		ids(search().param("q", "' OR 1=1 --"));
	}

	@Test
	void filtersCombineWithAndFieldsMatchAnyStacksMatchAll() throws Exception {
		ids(search().param("jobRole", "BACKEND"), alpha);
		// 화면 라벨은 ProjectRoleValues로 코드로 바꿔 비교한다.
		ids(search().param("jobRole", "백엔드"), alpha);
		ids(search().param("jobRole", "UI/UX"), gamma);
		ids(search().param("jobRole", "디자이너"), gamma);
		ids(search().param("jobRole", "보안 엔지니어"), beta);
		ids(search().param("jobRole", "없는 직군"));
		ids(search().param("interests", "핀테크", "커뮤니티"), alpha, epsilon);
		ids(search().param("techStacks", "React", "Figma"), gamma);
		ids(search().param("techStacks", "react"), alpha, gamma);
		ids(search().param("q", "python").param("interests", "AI"), delta);
		ids(search().param("status", "RECRUITING").param("techStacks", "React").param("jobRole", "FRONTEND"), alpha);
		ids(search().param("techStacks", "Spring Boot,React"));
	}

	@Test
	void pagesAndInvalidRequests() throws Exception {
		mvc.perform(search().param("size", "2").param("page", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].id", contains((int) alpha)))
			.andExpect(jsonPath("$.totalCount").value(5)).andExpect(jsonPath("$.totalPages").value(3));
		mvc.perform(search().param("size", "2").param("page", "9"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.totalCount").value(5)).andExpect(jsonPath("$.totalPages").value(3));

		String[] manyValues = IntStream.range(0, 31).mapToObj(i -> "v" + i).toArray(String[]::new);
		MockHttpServletRequestBuilder[] requests = {
			search().param("q", "a".repeat(101)),
			search().param("status", "모집 중"),
			search().param("status", "RECRUITING", "COMPLETED"),
			search().param("jobRole", "a".repeat(31)),
			search().param("jobRole", "백엔드\u0007"),
			search().param("interests", "a".repeat(31)),
			search().param("techStacks", manyValues),
			search().param("page", "-1"),
			search().param("size", "101"),
		};
		for (MockHttpServletRequestBuilder request : requests) {
			mvc.perform(request).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.message").isNotEmpty());
		}
	}
}
