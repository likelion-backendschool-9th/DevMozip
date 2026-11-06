package com.devmozip.backend.project;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import com.devmozip.backend.dto.project.CreateProjectRequest;
import com.devmozip.backend.dto.project.MyProjectsTab;
import com.devmozip.backend.dto.project.ProjectSearchRequest;
import com.devmozip.backend.entity.enums.ProjectJobRole;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.repository.ProjectQueryRepository;
import com.devmozip.backend.service.ProjectQueryService;
import com.devmozip.backend.service.ProjectService;
import com.devmozip.backend.service.impl.ProjectQueryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/** 팀 DB 명시적 opt-in. 기존 공고 조회수는 변경하지 않고 임시 데이터는 롤백한다. */
@SpringJUnitConfig({ProjectTeamDatabaseIntegrationTest.ProjectOnlyConfiguration.class,
	ProjectQueryTeamDatabaseIntegrationTest.QueryConfiguration.class})
@EnabledIfEnvironmentVariable(named = "PROJECT_TEAM_DB_TEST", matches = "true")
class ProjectQueryTeamDatabaseIntegrationTest {
	@Autowired ProjectQueryService queries;
	@Autowired ProjectService service;
	@Autowired JdbcTemplate jdbc;
	@Autowired PlatformTransactionManager transactions;
	@Autowired EntityManagerFactory factory;
	private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

	private ProjectSearchRequest search(String term, ProjectSearchRequest.Sort sort) {
		return new ProjectSearchRequest(term, null, null, null, null, sort, 0, 100);
	}
	private long fixture(String name, boolean visible, List<String> stacks, String interest, int days, List<Long> ids) {
		var result = service.create(new CreateProjectRequest(name, "검증", "#45 팀 DB 검증용 롤백 공고", null,
			"http://localhost:3000/portfolio-cover.svg", visible,
			List.of(new CreateProjectRequest.RecruitmentSlot(ProjectJobRole.BACKEND, 2, 0)),
			stacks, List.of(interest), today, today.plusDays(days), today, today.plusMonths(3)));
		ids.add(result.id()); return result.id();
	}
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
	@Test void readsExistingPublicRowsAndSortsWithoutChangingThem() {
		var tx = new TransactionTemplate(transactions); tx.setReadOnly(true);
		tx.executeWithoutResult(status -> {
			var latest = queries.search(search(null, ProjectSearchRequest.Sort.LATEST));
			assertEquals(jdbc.queryForObject("SELECT count(*) FROM projects WHERE is_public AND deleted_at IS NULL", Long.class), latest.totalElements());
			assertEquals(jdbc.queryForList("SELECT id FROM projects WHERE is_public AND deleted_at IS NULL ORDER BY created_at DESC,id DESC LIMIT 100", Long.class), latest.items().stream().map(p -> p.id()).toList());
			var popular = queries.search(search(null, ProjectSearchRequest.Sort.POPULAR));
			assertEquals(jdbc.queryForList("SELECT id FROM projects WHERE is_public AND deleted_at IS NULL ORDER BY view_count DESC,created_at DESC,id DESC LIMIT 100", Long.class), popular.items().stream().map(p -> p.id()).toList());
			var deadline = queries.search(search(null, ProjectSearchRequest.Sort.DEADLINE));
			assertEquals(jdbc.queryForList("SELECT id FROM projects WHERE is_public AND deleted_at IS NULL AND status='RECRUITING' AND recruitment_end>=? ORDER BY recruitment_end,created_at DESC,id DESC LIMIT 100", Long.class, today), deadline.items().stream().map(p -> p.id()).toList());
		});
	}
	@Test void filtersJsonbWithInterestOrAndStackAndAndPaginates() {
		rollback(ids -> {
			String key = "db45_" + UUID.randomUUID();
			long first = fixture(key + " Alpha", true, List.of("React", "Java"), "교육", 1, ids);
			long second = fixture(key + " Beta", true, List.of("React"), "게임", 2, ids);
			fixture(key + " Private", false, List.of("React", "Java"), "교육", 1, ids);
			var filtered = queries.search(new ProjectSearchRequest(key, ProjectJobRole.BACKEND, ProjectStatus.RECRUITING,
				List.of("교육", "게임"), List.of(" react ", "JAVA"), null, 0, 10));
			assertEquals(List.of(first), filtered.items().stream().map(p -> p.id()).toList());
			var page = queries.search(new ProjectSearchRequest(key, null, null, List.of("교육", "게임"), List.of("React"), null, 0, 1));
			assertEquals(2, page.totalElements()); assertEquals(2, page.totalPages()); assertEquals(1, page.items().size());
			var imminent = queries.search(search(key, ProjectSearchRequest.Sort.DEADLINE));
			assertEquals(List.of(first, second), imminent.items().stream().map(p -> p.id()).toList());
			assertTrue(queries.search(search(key + "missing", null)).items().isEmpty());
		});
	}
	@Test void handlesPrivateAccessViewsOwnListsAndDeletedRows() {
		rollback(ids -> {
			String key = "db45_" + UUID.randomUUID();
			long publicId = fixture(key + " Public", true, List.of("Java"), "교육", 1, ids);
			long privateId = fixture(key + " Private", false, List.of("Java"), "교육", 1, ids);
			long deletedId = fixture(key + " Deleted", true, List.of("Java"), "교육", 1, ids);
			jdbc.update("UPDATE projects SET deleted_at=localtimestamp WHERE id=?", deletedId);
			SharedEntityManagerCreator.createSharedEntityManager(factory).clear();
			assertEquals(404, assertThrows(ResponseStatusException.class, () -> queries.detail(privateId, false)).getStatusCode().value());
			assertEquals(1, queries.detail(publicId, false).project().viewCount());
			assertEquals(2, queries.detail(publicId, false).project().viewCount());
			SharedEntityManagerCreator.createSharedEntityManager(factory).flush();
			assertEquals(2L, jdbc.queryForObject("SELECT view_count FROM projects WHERE id=?", Long.class, publicId));
			assertEquals(privateId, queries.detail(privateId, true).project().id());
			assertEquals(1, queries.manage(publicId).memberCount());
			var mine = queries.mine(search(key, null), MyProjectsTab.HOSTED);
			assertEquals(2, mine.totalElements()); assertTrue(mine.items().stream().allMatch(p -> "HOST".equals(p.myRole())));
			assertEquals(1, queries.search(search(key, null)).totalElements());
			assertEquals(404, assertThrows(ResponseStatusException.class, () -> queries.detail(deletedId, false)).getStatusCode().value());
			assertEquals(501, assertThrows(ResponseStatusException.class, () -> queries.mine(search(key, null), MyProjectsTab.APPLYING)).getStatusCode().value());
		});
	}
	@TestConfiguration(proxyBeanMethods = false)
	@Import({ProjectQueryServiceImpl.class, ProjectQueryRepository.class})
	static class QueryConfiguration {
		@Bean NamedParameterJdbcTemplate namedJdbc(DataSource source) { return new NamedParameterJdbcTemplate(source); }
	}
}
