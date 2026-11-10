package com.devmozip.backend.message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import com.devmozip.backend.entity.Member;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class UserReportApiIntegrationTest extends UserReportApiTests {
	@Container @ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	@Test void tableHasBlueprintColumnsAndBaseEntityTimes() {
		List<String> columns = jdbc.queryForList(
			"SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'reports'", String.class);
		assertEquals(new TreeSet<>(List.of("id", "reporter_id", "reported_user_id", "project_id", "post_id", "comment_id", "handled_by",
			"reason", "status", "resolution", "handled_at", "created_at", "updated_at")), new TreeSet<>(columns));
	}

	@Test void concurrentDuplicatesCreateOneReport() throws Exception {
		Member reporter = member("reporter");
		Member target = member("target");
		List<Callable<Integer>> reports = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			reports.add(() -> postAs(reporter, reports(target), Map.of("reason", "사칭")).andReturn().getResponse().getStatus());
		}
		List<Integer> statuses = runTogether(reports);
		assertEquals(1, statuses.stream().filter(status -> status == 201).count(), statuses.toString());
		assertEquals(7, statuses.stream().filter(status -> status == 409).count(), statuses.toString());
		assertEquals(1, reportCount(reporter));
	}
}
