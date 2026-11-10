package com.devmozip.backend.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.devmozip.backend.entity.enums.NotificationType;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import com.devmozip.backend.service.NotificationDraft;
import com.devmozip.backend.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** WBS 12.1·12.2 알림 조회·읽음 API와 생성 경계. 테이블과 데이터는 이 테스트 전용 PostgreSQL 컨테이너에만 만든다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationApiIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;
	@Autowired NotificationService service;

	static final String BASE = "/api/v1/notifications";

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.sql.init.mode", () -> "never");
	}

	@BeforeEach
	void fixtures() {
		jdbc.update("DELETE FROM notifications");
		jdbc.update("DELETE FROM users");
		for (long id : new long[] {1, 2}) {
			jdbc.update("INSERT INTO users(id, email, nickname, role, status) VALUES (?, ?, ?, 'USER', 'ACTIVE')",
				id, "notice" + id + "@test.local", "회원" + id);
		}
	}

	MockHttpServletRequestBuilder as(long id, MockHttpServletRequestBuilder request) {
		return request.header("Authorization", "Bearer " + jwt.createAccessToken("notice" + id + "@test.local", "USER"));
	}

	NotificationDraft draft(long recipient, String token) {
		return new NotificationDraft(recipient, null, null, token, NotificationType.PROJECT_STATUS_CHANGED,
			"프로젝트 상태 변경", "프로젝트: 모집 완료", "/projects/7");
	}

	long create(long recipient, String token) {
		assertTrue(service.create(draft(recipient, token)));
		return jdbc.queryForObject("SELECT id FROM notifications WHERE recipient_id = ? AND token = ?", Long.class, recipient, token);
	}

	String readAt(long id) {
		return jdbc.queryForObject("SELECT to_char(read_at, 'YYYY-MM-DD HH24:MI:SS.US') FROM notifications WHERE id = ?", String.class, id);
	}

	MockHttpServletRequestBuilder readRequest(long id, String body) {
		var request = patch(BASE + "/" + id + "/read");
		return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	@Test
	void everyPathRequiresLogin() throws Exception {
		long id = create(1, "a");
		mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
		mvc.perform(readRequest(id, null)).andExpect(status().isUnauthorized());
		mvc.perform(patch(BASE + "/read-all")).andExpect(status().isUnauthorized());
		assertNull(readAt(id));
	}

	@Test
	void listShowsOnlyOwnNotificationsWithoutTokenOrReviewId() throws Exception {
		create(1, "older");
		long newest = create(1, "newer");
		create(2, "other");
		mvc.perform(as(1, get(BASE))).andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].id").value(newest))
			.andExpect(jsonPath("$.items[0].type").value("PROJECT_STATUS_CHANGED"))
			.andExpect(jsonPath("$.items[0].title").value("프로젝트 상태 변경"))
			.andExpect(jsonPath("$.items[0].body").value("프로젝트: 모집 완료"))
			.andExpect(jsonPath("$.items[0].actionUrl").value("/projects/7"))
			.andExpect(jsonPath("$.items[0].projectId").isEmpty())
			.andExpect(jsonPath("$.items[0].read").value(false))
			.andExpect(jsonPath("$.items[0].resolved").value(false))
			.andExpect(jsonPath("$.items[0].review").isEmpty())
			.andExpect(jsonPath("$.items[0].createdAt").isString())
			.andExpect(jsonPath("$.items[0].token").doesNotExist())
			.andExpect(jsonPath("$.items[0].reviewId").doesNotExist())
			.andExpect(jsonPath("$.items[0].recipientId").doesNotExist())
			.andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.hasNext").value(false)).andExpect(jsonPath("$.unreadCount").value(2));
		mvc.perform(as(2, get(BASE))).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.unreadCount").value(1));
	}

	@Test
	void readIsIdempotentAndCanBeChangedBackToUnread() throws Exception {
		long id = create(1, "a");
		create(1, "b");
		mvc.perform(as(1, readRequest(id, null))).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.read").value(true)).andExpect(jsonPath("$.unreadCount").value(1));
		String first = readAt(id);
		assertNotNull(first);
		mvc.perform(as(1, readRequest(id, "{\"read\":true}"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.read").value(true)).andExpect(jsonPath("$.unreadCount").value(1));
		assertEquals(first, readAt(id));
		mvc.perform(as(1, readRequest(id, "{\"read\":false}"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.read").value(false)).andExpect(jsonPath("$.unreadCount").value(2));
		assertNull(readAt(id));
		mvc.perform(as(1, readRequest(id, "{\"read\":false}"))).andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(2));
	}

	@Test
	void otherUsersOrMissingNotificationIsNotFoundAndUnchanged() throws Exception {
		long others = create(2, "a");
		mvc.perform(as(1, readRequest(others, null))).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("알림을 찾을 수 없습니다."));
		mvc.perform(as(1, readRequest(others, "{\"read\":false}"))).andExpect(status().isNotFound());
		mvc.perform(as(1, readRequest(999_999, null))).andExpect(status().isNotFound());
		assertNull(readAt(others));
	}

	@Test
	void tabsUnreadCountAndReadAllAreSeparatedByRecipient() throws Exception {
		long read = create(1, "a");
		create(1, "b");
		create(1, "c");
		long others = create(2, "d");
		mvc.perform(as(1, readRequest(read, null))).andExpect(status().isOk());
		mvc.perform(as(1, get(BASE).param("status", "unread"))).andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].read").value(false)).andExpect(jsonPath("$.unreadCount").value(2));
		mvc.perform(as(1, get(BASE).param("status", "read"))).andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].id").value(read)).andExpect(jsonPath("$.unreadCount").value(2));
		mvc.perform(as(1, get(BASE).param("status", "all"))).andExpect(jsonPath("$.items.length()").value(3));
		String firstRead = readAt(read);
		mvc.perform(as(1, patch(BASE + "/read-all"))).andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(0));
		mvc.perform(as(1, patch(BASE + "/read-all"))).andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(0));
		assertEquals(firstRead, readAt(read));
		assertNull(readAt(others));
		mvc.perform(as(2, get(BASE))).andExpect(jsonPath("$.unreadCount").value(1));
	}

	@Test
	void pagesAreNewestFirst() throws Exception {
		long a = create(1, "a");
		long b = create(1, "b");
		long c = create(1, "c");
		mvc.perform(as(1, get(BASE).param("size", "2"))).andExpect(jsonPath("$.items[0].id").value(c))
			.andExpect(jsonPath("$.items[1].id").value(b)).andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.unreadCount").value(3));
		mvc.perform(as(1, get(BASE).param("size", "2").param("page", "1"))).andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].id").value(a)).andExpect(jsonPath("$.hasNext").value(false))
			.andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(2));
		mvc.perform(as(1, get(BASE).param("size", "100"))).andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = {"status=bad", "status=READ", "status=read&status=unread", "page=-1", "size=0", "size=101", "page=abc", "size=1.5"})
	void invalidQueryIsBadRequest(String query) throws Exception {
		mvc.perform(as(1, get(BASE + "?" + query))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400));
	}

	@ParameterizedTest
	@ValueSource(strings = {"{", "{}", "{\"read\":null}", "{\"read\":\"maybe\"}", "[true]"})
	void invalidReadBodyIsBadRequestAndUnchanged(String body) throws Exception {
		long id = create(1, "a");
		mvc.perform(as(1, readRequest(id, body))).andExpect(status().isBadRequest());
		assertNull(readAt(id));
	}

	@Test
	void nonNumericIdIsBadRequest() throws Exception {
		mvc.perform(as(1, patch(BASE + "/abc/read"))).andExpect(status().isBadRequest());
	}

	@Test
	void sameTokenIsCreatedOnceEvenConcurrently() throws Exception {
		assertTrue(service.create(draft(1, "same")));
		assertFalse(service.create(draft(1, "same")));
		assertTrue(service.create(draft(2, "same")));

		int threads = 8;
		var pool = Executors.newFixedThreadPool(threads);
		var start = new CountDownLatch(1);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				Callable<Boolean> task = () -> {
					start.await();
					return service.create(draft(1, "concurrent"));
				};
				results.add(pool.submit(task));
			}
			start.countDown();
			int created = 0;
			for (var result : results) if (result.get(20, TimeUnit.SECONDS)) created++;
			assertEquals(1, created);
		} finally {
			pool.shutdownNow();
		}
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id = 1 AND token = 'concurrent'", Integer.class));
		assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class));
	}

	@ParameterizedTest
	@ValueSource(strings = {"//evil.example", "/\\evil.example", "https://evil.example", "projects/1", "/projects/1 x", ""})
	void createAcceptsOnlyInternalActionUrls(String actionUrl) {
		var invalid = new NotificationDraft(1, null, null, "url", NotificationType.PROJECT_COMPLETED, "프로젝트 종료", "본문", actionUrl);
		assertThrows(IllegalArgumentException.class, () -> service.create(invalid));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class));
	}
}
