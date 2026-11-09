package com.devmozip.backend.project;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import com.devmozip.backend.controller.ProjectController;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.entity.ProjectMember;
import com.devmozip.backend.repository.ProjectRepository;
import com.devmozip.backend.repository.ProjectMemberRepository;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import com.devmozip.backend.service.ProjectApplicationUsage;
import com.devmozip.backend.service.ProjectPeriodPolicy;
import com.devmozip.backend.service.ProjectQueryService;
import com.devmozip.backend.service.ProjectService;
import com.devmozip.backend.service.impl.ProjectServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 팀 DB 명시적 opt-in. 프로젝트 테이블만 validate하며 생성 데이터는 항상 롤백한다.
 * standalone MVC 검증이므로 JWT 필터·실제 회원·프리미엄 인가 검증은 포함하지 않는다.
 */
@SpringJUnitConfig(ProjectTeamDatabaseIntegrationTest.ProjectOnlyConfiguration.class)
@EnabledIfEnvironmentVariable(named = "PROJECT_TEAM_DB_TEST", matches = "true")
class ProjectTeamDatabaseIntegrationTest {
	@Autowired ProjectService service;
	@Autowired JdbcTemplate jdbc;
	@Autowired PlatformTransactionManager transactions;

	@Test void createApiStoresProjectAndHostInTeamPostgresAndRollsBack() {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		var mvc = MockMvcBuilders.standaloneSetup(new ProjectController(service, mock(ProjectQueryService.class))).build();
		long[] createdId = {0};
		new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
			try {
				String body = """
					{"title":"#44 팀 DB 연결 검증 (롤백)","description":"프로젝트 생성·JSONB·주최자 저장 확인",
					 "imageUrl":"http://localhost:3000/portfolio-cover.svg","isPublic":true,
					 "recruitmentSlots":[{"roleCode":"BACKEND","capacity":2,"minimumYears":0}],
					 "techStacks":[" Spring Boot ","PostgreSQL"],"interests":["교육"],
					 "recruitmentStart":"%s","recruitmentEnd":"%s",
					 "developmentStart":"%s","developmentEnd":"%s"}
					""".formatted(today, today.plusDays(14), today.plusDays(15), today.plusDays(90));
				var result = mvc.perform(post("/api/v1/projects").contentType("application/json").content(body))
					.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("RECRUITING"))
					.andExpect(jsonPath("$.techStacks[0]").value("Spring Boot"))
					.andExpect(jsonPath("$.totalCapacity").value(3)).andReturn();
				long id = new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("id").asLong();
				createdId[0] = id;
				assertTrue(id > 0);
				assertEquals("/api/v1/projects/" + id, result.getResponse().getHeader("Location"));
				assertEquals("jsonb", jdbc.queryForObject("SELECT pg_typeof(recruitment_slots)::text FROM projects WHERE id=?", String.class, id));
				assertEquals("BACKEND", jdbc.queryForObject("SELECT recruitment_slots->0->>'roleCode' FROM projects WHERE id=?", String.class, id));
				assertEquals("교육", jdbc.queryForObject("SELECT interests->>0 FROM projects WHERE id=?", String.class, id));
				var host = jdbc.queryForMap("SELECT user_id,role,status,job_role FROM project_members WHERE project_id=?", id);
				assertEquals(101L, ((Number) host.get("user_id")).longValue());
				assertEquals("HOST", host.get("role"));
				assertEquals("ACTIVE", host.get("status"));
				assertNull(host.get("job_role"));
			} catch (Exception exception) {
				throw new AssertionError(exception);
			} finally {
				transaction.setRollbackOnly();
			}
		});
		assertTrue(createdId[0] > 0);
		assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM projects WHERE id=?", Long.class, createdId[0]));
		assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM project_members WHERE project_id=?", Long.class, createdId[0]));
	}

	@TestConfiguration(proxyBeanMethods = false)
	@EnableTransactionManagement
	@Import({ProjectServiceImpl.class, ProjectPeriodPolicy.class})
	static class ProjectOnlyConfiguration {
		@Bean DataSource dataSource() {
			String url = required("PROJECT_TEAM_DB_URL");
			if (!url.startsWith("jdbc:postgresql:")) throw new IllegalArgumentException("PostgreSQL URL이 필요합니다.");
			return new DriverManagerDataSource(url, required("PROJECT_TEAM_DB_USER"), required("PROJECT_TEAM_DB_PASSWORD"));
		}
		private static String required(String key) {
			String value = System.getenv(key);
			if (value == null || value.isBlank()) throw new IllegalStateException(key + " 환경변수가 필요합니다.");
			return value;
		}
		@Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
			var factory = new LocalContainerEntityManagerFactoryBean();
			factory.setDataSource(source);
			factory.setManagedTypes(PersistenceManagedTypes.of(Project.class.getName(), ProjectMember.class.getName()));
			factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
			factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
			return factory;
		}
		@Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
		@Bean ProjectRepository projectRepository(EntityManagerFactory factory) {
			return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory)).getRepository(ProjectRepository.class);
		}
		@Bean ProjectMemberRepository projectMemberRepository(EntityManagerFactory factory) {
			return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory)).getRepository(ProjectMemberRepository.class);
		}
		@Bean LocalValidatorFactoryBean validator() { return new LocalValidatorFactoryBean(); }
		@Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
		@Bean ProjectCurrentUserProvider currentUser() { return () -> 101L; }
		@Bean ProjectApplicationUsage applications() { return id -> Set.of(); }
		@Bean("projectClock") Clock clock() { return Clock.system(ZoneId.of("Asia/Seoul")); }
	}
}
