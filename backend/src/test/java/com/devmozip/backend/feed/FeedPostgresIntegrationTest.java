package com.devmozip.backend.feed;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.devmozip.backend.entity.Member;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 피드 매핑·목록 SQL(2단계)과 API(3·4단계)를 이 테스트 전용 PostgreSQL 컨테이너에서 확인한다.
 * 세 묶음을 @Nested로 묶고, 쓴 컨텍스트는 바로 닫는다(@DirtiesContext). 전체 테스트에서 캐시된 컨텍스트가 늘면
 * 테스트 JVM 힙(기본 512MB)이 모자라 뒤에 실행되는 WebSocket 테스트가 실패했다(2026-11-10 확인).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FeedPostgresIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.sql.init.mode", () -> "never");
	}

	@Nested
	class Repository extends PostRepositoryTests {
		@Test
		void createsPostColumnsWithJsonbArrays() {
			Map<String, String> types = new TreeMap<>();
			jdbc.query("SELECT column_name, data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'posts'",
				rs -> { types.put(rs.getString(1), rs.getString(2)); });
			assertEquals(List.of("author_id", "category", "content", "created_at", "deleted_at", "id", "image_urls", "interests",
				"original_post_id", "tech_stacks", "title", "type", "updated_at", "view_count", "visibility"), List.copyOf(types.keySet()));
			assertEquals("jsonb", types.get("tech_stacks"));
			assertEquals("jsonb", types.get("interests"));
			assertEquals("jsonb", types.get("image_urls"));
			assertEquals("timestamp without time zone", types.get("created_at"));
			assertEquals("bigint", types.get("view_count"));
		}

		@Test
		void createsCommentColumnsFromErd() {
			List<String> columns = jdbc.queryForList("SELECT column_name FROM information_schema.columns"
				+ " WHERE table_schema = 'public' AND table_name = 'comments' ORDER BY column_name", String.class);
			assertEquals(List.of("author_id", "content", "created_at", "deleted_at", "id", "post_id", "updated_at"), columns);
		}
	}

	@Nested
	class Posts extends FeedPostApiTests {
	}

	@Nested
	class Interactions extends FeedInteractionApiTests {
		/** 같은 요청을 동시에 보내고 상태 코드별 개수를 돌려준다. */
		Map<Integer, Integer> concurrently(int times, Callable<Integer> request) throws Exception {
			ExecutorService pool = Executors.newFixedThreadPool(times);
			CountDownLatch start = new CountDownLatch(1);
			try {
				List<Future<Integer>> results = new ArrayList<>();
				for (int i = 0; i < times; i++) {
					results.add(pool.submit(() -> {
						start.await();
						return request.call();
					}));
				}
				start.countDown();
				Map<Integer, Integer> statuses = new TreeMap<>();
				for (Future<Integer> result : results) statuses.merge(result.get(), 1, Integer::sum);
				return statuses;
			} finally {
				pool.shutdownNow();
			}
		}

		@Test
		void concurrentDuplicateReportsAndRepostsSaveOnce() throws Exception {
			Member writer = member("writer");
			Member reader = member("reader");
			long postId = write(writer, "동시 요청");
			Map<Integer, Integer> reports = concurrently(8, () -> send(reader, post(FEEDS + "/" + postId + "/reports"),
				Map.of("reason", "기타")).andReturn().getResponse().getStatus());
			assertEquals(Map.of(201, 1, 409, 7), reports);
			assertEquals(1, count("SELECT count(*) FROM reports"));

			Map<Integer, Integer> reposts = concurrently(8, () -> send(reader, post(FEEDS + "/" + postId + "/reposts"),
				Map.of("content", "동시에")).andReturn().getResponse().getStatus());
			assertEquals(Map.of(201, 1, 409, 7), reposts);
			assertEquals(1, count("SELECT count(*) FROM posts WHERE original_post_id = ? AND deleted_at IS NULL", postId));
		}

		@Test
		void concurrentLikesFromManyMembersAreAllCounted() throws Exception {
			Member writer = member("writer");
			long postId = write(writer, "동시 좋아요");
			List<Member> readers = new ArrayList<>();
			for (int i = 0; i < 8; i++) readers.add(member("reader"));
			ExecutorService pool = Executors.newFixedThreadPool(readers.size());
			CountDownLatch start = new CountDownLatch(1);
			try {
				List<Future<Integer>> results = new ArrayList<>();
				for (Member reader : readers) {
					results.add(pool.submit(() -> {
						start.await();
						return send(reader, patch(FEEDS + "/" + postId + "/likes"), null).andReturn().getResponse().getStatus();
					}));
				}
				start.countDown();
				for (Future<Integer> result : results) assertEquals(200, result.get());
			} finally {
				pool.shutdownNow();
			}
			assertEquals(8, detail(null, postId).get("likeCount").asLong());
		}
	}
}
