package com.devmozip.backend.ai.engine;

import com.devmozip.backend.dto.ai.*;
import com.devmozip.backend.dto.ai.internal.*;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.entity.ProjectRecruitmentSlot;
import com.devmozip.backend.entity.enums.ProjectJobRole;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.ProjectRepository;
import com.devmozip.backend.security.ai.CurrentUserProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.service.AiRecommendationService;
import com.devmozip.backend.config.AiScoringProperties;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import static com.devmozip.backend.ai.engine.AiRecommendationScoringTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** 기본은 격리 H2. 명시적 테스트 전용 JDBC URL이 있으면 같은 검증을 PostgreSQL에서도 실행한다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AiRecommendationDatabaseTest {
	@Autowired AiRecommendationService engine;
	@Autowired MemberRepository members;
	@Autowired ProjectRepository projects;
	@Autowired EntityManager entityManager;
	@Autowired JdbcTemplate jdbc;
	@Autowired AiScoringProperties properties;
	@MockitoBean CurrentUserProvider requester;
	@MockitoBean RefreshTokenStore tokens;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		String configured = System.getenv("DEVMOZIP_AI_TEST_JDBC_URL");
		boolean postgres = configured != null;
		registry.add("spring.datasource.url", () -> postgres ? configured :
			"jdbc:h2:mem:ai-recommendation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON");
		registry.add("spring.datasource.driver-class-name", () -> postgres ? "org.postgresql.Driver" : "org.h2.Driver");
		registry.add("spring.datasource.username", () -> postgres ? "ai_engine_test" : "sa");
		registry.add("spring.datasource.password", () -> "");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		registry.add("spring.sql.init.mode", () -> "never");
		registry.add("management.health.redis.enabled", () -> "false");
	}

	Member member(String tag, Member.MemberStatus status, Member.CollaborationStatus available, List<String> roles) {
		return members.saveAndFlush(Member.builder().nickname(tag).tag(tag).status(status).collaborationStatus(available)
			.role(Member.MemberRole.USER).career("3년 차").jobCategories(roles).skills(List.of("Java"))
			.interests(List.of("교육")).trustScore(n("36.5")).build());
	}
	Project project(boolean visible) {
		var start = LocalDate.of(2026, 1, 31);
		return projects.saveAndFlush(new Project(999, "교육 플랫폼", null, "교육 플랫폼", null, "https://example.com/a.png",
			visible, List.of(new ProjectRecruitmentSlot(ProjectJobRole.BACKEND.name(), 1, 3)), List.of("Java", "React"), List.of("교육"),
			start.minusDays(2), start, start, start.plusMonths(1), LocalDateTime.of(2026, 1, 1, 0, 0)));
	}

	@Test void databaseFiltersAvailabilityPublicProfileAccountAndRequesterAndDoesNotPersistResults() {
		var owner = member("owner", Member.MemberStatus.ACTIVE, Member.CollaborationStatus.AVAILABLE, List.of("백엔드 개발자"));
		when(requester.getCurrentUserId()).thenReturn(owner.getId());
		var eligible = member("eligible", Member.MemberStatus.ACTIVE, Member.CollaborationStatus.AVAILABLE, List.of("백엔드 개발자"));
		member("suspended", Member.MemberStatus.SUSPENDED, Member.CollaborationStatus.AVAILABLE, List.of("백엔드 개발자"));
		member("warned", Member.MemberStatus.WARNED, Member.CollaborationStatus.AVAILABLE, List.of("백엔드 개발자"));
		member("no-jobs", Member.MemberStatus.ACTIVE, Member.CollaborationStatus.AVAILABLE, List.of());
		var noTag = member("no-tag", Member.MemberStatus.ACTIVE, Member.CollaborationStatus.AVAILABLE, List.of("백엔드 개발자"));
		jdbc.update("UPDATE users SET tag=null WHERE id=?", noTag.getId());
		for (var status : Member.CollaborationStatus.values()) {
			if (status != Member.CollaborationStatus.AVAILABLE) member("status-" + status, Member.MemberStatus.ACTIVE, status, List.of("백엔드 개발자"));
		}
		member("unknown", Member.MemberStatus.ACTIVE, null, List.of("백엔드 개발자"));
		long usersBefore = members.count();
		long conversationsBefore = jdbc.queryForObject("SELECT count(*) FROM ai_conversations", Long.class);
		entityManager.clear();
		var result = engine.recommend(defaults(), "교육 플랫폼");
		assertEquals(List.of(eligible.getId()), result.developers().stream().map(RecommendationResult.Developer::id).toList());
		assertEquals(n("64.29"), result.developers().getFirst().score().total());
		assertEquals(n("50"), properties.trustCeiling());
		assertEquals(usersBefore, members.count());
		assertEquals(conversationsBefore, jdbc.queryForObject("SELECT count(*) FROM ai_conversations", Long.class));
		assertEquals(result, engine.recommend(defaults(), "교육 플랫폼"));
	}

	@Test void databaseExcludesPrivateAndDeletedProjectsAndUsesActualJsonData() {
		when(requester.getCurrentUserId()).thenReturn(999L);
		var visible = project(true);
		project(false);
		var deleted = project(true);
		jdbc.update("UPDATE projects SET deleted_at=current_timestamp WHERE id=?", deleted.getId());
		entityManager.clear();
		var result = engine.recommend(defaults(), "교육 플랫폼");
		assertEquals(List.of(visible.getId()), result.projects().stream().map(RecommendationResult.SimilarProject::id).toList());
		assertEquals(n("100.00"), result.projects().getFirst().score().total());
		assertEquals(0L, result.projects().getFirst().duration().differenceDays());
		assertEquals(3, projects.count());
	}

	@Test void unavailableRequestedJobsReturnNoDevelopersWhileProjectsRemainAndStackMismatchDoesNotFilter() {
		when(requester.getCurrentUserId()).thenReturn(999L);
		member("backend", Member.MemberStatus.ACTIVE, Member.CollaborationStatus.AVAILABLE, List.of("백엔드 개발자"));
		project(true);
		var missingJobs = conditions(List.of(new RecruitmentRoleConditionDto("게임", 1),
			new RecruitmentRoleConditionDto("모바일", 1), new RecruitmentRoleConditionDto("UI/UX", 1)),
			List.of("React"), com.devmozip.backend.entity.enums.RequiredCareer.ANY);
		var empty = engine.recommend(missingJobs, null);
		assertTrue(empty.developers().isEmpty());
		assertEquals(1, empty.projects().size());
		assertTrue(empty.roleCoverage().stream().allMatch(role -> role.eligibleCount() == 0));
		var matchingJob = conditions(List.of(new RecruitmentRoleConditionDto("백엔드", 1)),
			List.of("React"), com.devmozip.backend.entity.enums.RequiredCareer.ANY);
		assertEquals(1, engine.recommend(matchingJob, null).developers().size());
	}

	@Test void keysetScanScoresCandidatesAfterFirstDatabaseBatchBeforeLimitingToTen() {
		when(requester.getCurrentUserId()).thenReturn(999999L);
		for (int i = 0; i < 202; i++) member("paged-" + i, Member.MemberStatus.ACTIVE,
			Member.CollaborationStatus.AVAILABLE, List.of("백엔드 개발자"));
		var best = member("best", Member.MemberStatus.ACTIVE, Member.CollaborationStatus.AVAILABLE, List.of("백엔드 개발자"));
		jdbc.update("UPDATE users SET trust_score=50 WHERE id=?", best.getId());
		entityManager.clear();
		var result = engine.recommend(defaults(), null);
		assertEquals(10, result.developers().size());
		assertEquals(best.getId().longValue(), result.developers().getFirst().id());
		assertEquals(203, result.roleCoverage().getFirst().eligibleCount());
	}
}
