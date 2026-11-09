package com.devmozip.backend.project;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import jakarta.persistence.EntityManagerFactory;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import com.devmozip.backend.service.ProjectQueryService;
import com.devmozip.backend.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** #46 팀 DB opt-in. 임시 공고만 수정·삭제하며 모든 변경을 롤백한다. */
@SpringJUnitConfig({ProjectTeamDatabaseIntegrationTest.ProjectOnlyConfiguration.class,
	ProjectQueryTeamDatabaseIntegrationTest.QueryConfiguration.class})
@EnabledIfEnvironmentVariable(named = "PROJECT_TEAM_DB_TEST", matches = "true")
class ProjectUpdateDeleteTeamDatabaseIntegrationTest {
	@Autowired ProjectService service;
	@Autowired ProjectQueryService queries;
	@Autowired JdbcTemplate jdbc;
	@Autowired PlatformTransactionManager transactions;
	@Autowired EntityManagerFactory factory;
	@MockitoBean ProjectCurrentUserProvider user;
	private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

	@BeforeEach void actor() { when(user.getCurrentUserId()).thenReturn(101L); }
	private long fixture(List<Long> ids) {
		var response = service.create(new CreateProjectRequest("db46_" + UUID.randomUUID(), "이전 소개", "#46 팀 DB 롤백 검증", "이전 목표",
			"http://localhost:3000/portfolio-cover.svg", true,
			List.of(new CreateProjectRequest.RecruitmentSlot("BACKEND", 2, 0)),
			List.of("Java"), List.of("교육"), today, today.plusDays(7), today, today.plusDays(90)));
		ids.add(response.id()); return response.id();
	}
	private UpdateProjectRequest update(int capacity) {
		return new UpdateProjectRequest("db46_수정 공고", null, "수정한 프로젝트 상세 소개", null,
			"https://example.test/changed.png", false,
			List.of(new CreateProjectRequest.RecruitmentSlot("BACKEND", capacity, 1)),
			List.of(" Spring Boot ", "PostgreSQL"), List.of("생산성"), today, today.plusDays(14), today, today.plusDays(120));
	}
	private void clear() { SharedEntityManagerCreator.createSharedEntityManager(factory).clear(); }
	private void rollback(Consumer<List<Long>> action) {
		List<Long> ids = new ArrayList<>();
		try {
			new TransactionTemplate(transactions).executeWithoutResult(tx -> {
				try { action.accept(ids); } finally { tx.setRollbackOnly(); }
			});
		} finally {
			for (long id : ids) {
				assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM projects WHERE id=?", Long.class, id));
				assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM project_members WHERE project_id=?", Long.class, id));
			}
		}
	}
	private void rejected(int status, Runnable action) {
		assertEquals(status, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
	}
	@Test void replacesJsonbAndFieldsAndPreservesViewsCreationAndHost() {
		rollback(ids -> {
			long id = fixture(ids);
			var created = jdbc.queryForObject("SELECT created_at FROM projects WHERE id=?", java.sql.Timestamp.class, id);
			jdbc.update("UPDATE projects SET view_count=7 WHERE id=?", id); clear();
			var changed = service.update(id, update(3));
			assertEquals(List.of("Spring Boot", "PostgreSQL"), changed.techStacks());
			assertNull(changed.summary()); assertNull(changed.goal()); assertFalse(changed.isPublic());
			assertEquals(7, changed.viewCount()); assertEquals(4, changed.totalCapacity());
			assertEquals("Spring Boot", jdbc.queryForObject("SELECT tech_stacks->>0 FROM projects WHERE id=?", String.class, id));
			assertEquals("생산성", jdbc.queryForObject("SELECT interests->>0 FROM projects WHERE id=?", String.class, id));
			assertEquals(3, jdbc.queryForObject("SELECT (recruitment_slots->0->>'capacity')::int FROM projects WHERE id=?", Integer.class, id));
			assertEquals(created, jdbc.queryForObject("SELECT created_at FROM projects WHERE id=?", java.sql.Timestamp.class, id));
			assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM project_members WHERE project_id=? AND role='HOST' AND status='ACTIVE'", Long.class, id));
			clear(); assertEquals("db46_수정 공고", queries.manage(id).project().title());
		});
	}
	@Test void logicalDeleteKeepsRowsAndHidesPublicAndOwnQueries() {
		rollback(ids -> {
			long id = fixture(ids); String title = queries.manage(id).project().title();
			service.delete(id); clear();
			assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM projects WHERE id=?", java.sql.Timestamp.class, id));
			assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM project_members WHERE project_id=?", Long.class, id));
			var filter = new ProjectSearchRequest(title, null, null, null, null, null, 0, 20);
			assertEquals(0, queries.search(filter).totalElements());
			assertEquals(0, queries.mine(filter, MyProjectsTab.HOSTED).totalElements());
			rejected(404, () -> queries.detail(id, false)); rejected(404, () -> queries.manage(id));
			rejected(404, () -> service.update(id, update(3))); rejected(404, () -> service.delete(id));
		});
	}
	@Test void rejectsNonHostAndCompletedProjectChanges() {
		rollback(ids -> {
			long id = fixture(ids);
			when(user.getCurrentUserId()).thenReturn(202L);
			rejected(403, () -> service.update(id, update(3))); rejected(403, () -> service.delete(id));
			when(user.getCurrentUserId()).thenReturn(101L);
			jdbc.update("UPDATE projects SET status='COMPLETED',completed_at=localtimestamp WHERE id=?", id); clear();
			rejected(409, () -> service.update(id, update(3))); rejected(409, () -> service.delete(id));
			assertNull(jdbc.queryForObject("SELECT deleted_at FROM projects WHERE id=?", java.sql.Timestamp.class, id));
		});
	}
	@Test void refusesCapacityBelowActiveParticipants() {
		rollback(ids -> {
			long id = fixture(ids);
			for (long userId : List.of(202L, 203L)) jdbc.update("INSERT INTO project_members(project_id,user_id,role,job_role,status,joined_at,created_at,updated_at) VALUES (?,?,'PARTICIPANT','BACKEND','ACTIVE',localtimestamp,localtimestamp,localtimestamp)", id, userId);
			clear(); rejected(409, () -> service.update(id, update(1)));
			assertEquals(2, jdbc.queryForObject("SELECT (recruitment_slots->0->>'capacity')::int FROM projects WHERE id=?", Integer.class, id));
		});
	}
}
