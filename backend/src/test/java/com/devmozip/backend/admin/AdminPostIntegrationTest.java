package com.devmozip.backend.admin;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * WBS 14.6 게시글·댓글 관리, 14.7 표시 여부 (docs/admin-plan.md 3단계).
 * 관리자 숨김·삭제가 기존 피드 조회 규칙(작성자만 숨긴 글 상세, 삭제 글 404, 퍼간 글의 원본 안내)에 그대로 반영되는지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AdminPostIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore refreshTokens;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;

	static final String ADMIN_POSTS = "/api/v1/admin/posts";
	static final long ADMIN = 1, AUTHOR = 2, READER = 3;
	static final long POST = 10, HIDDEN = 11, REPOST = 12, OTHER = 13, DELETED = 14, COMMENT = 20, DELETED_COMMENT = 21;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.sql.init.mode", () -> "never");
	}

	@BeforeEach
	void fixtures() {
		jdbc.update("DELETE FROM comments");
		jdbc.update("DELETE FROM posts");
		jdbc.update("DELETE FROM users");
		user(ADMIN, "운영자", "admin", "ADMIN");
		user(AUTHOR, "작성자", "author", "USER");
		user(READER, "독자", "reader", "USER");
		post(POST, AUTHOR, "POST", null, "스프링 질문", "트랜잭션 100% 이해하기", "VISIBLE", false);
		post(HIDDEN, AUTHOR, "POST", null, "작성자가 숨긴 글", "숨김", "HIDDEN", false);
		post(REPOST, READER, "REPOST", POST, null, "좋은 글이에요", "VISIBLE", false);
		post(OTHER, READER, "POST", null, "다른 글", "광고 글", "VISIBLE", false);
		post(DELETED, AUTHOR, "POST", null, "지운 글", "삭제됨", "VISIBLE", true);
		comment(COMMENT, POST, READER, "광고 댓글입니다", false);
		comment(DELETED_COMMENT, POST, READER, "지운 댓글", true);
	}

	void user(long id, String nickname, String tag, String role) {
		jdbc.update("INSERT INTO users(id, email, nickname, tag, role, status, job_categories, skills, collaboration_status, career, is_private)"
			+ " VALUES (?, ?, ?, ?, ?, 'ACTIVE', '[\"백엔드 개발자\"]'::jsonb, '[\"Java\"]'::jsonb, 'AVAILABLE', '2년 차', false)",
			id, email(id), nickname, tag, role);
	}

	void post(long id, long author, String type, Long original, String title, String content, String visibility, boolean deleted) {
		jdbc.update("INSERT INTO posts(id, author_id, original_post_id, type, category, title, content, tech_stacks, interests, image_urls,"
			+ " view_count, visibility, deleted_at, created_at, updated_at) VALUES (?, ?, ?, ?, 'INFO', ?, ?, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb,"
			+ " 0, ?, ?, TIMESTAMP '2026-11-10 10:00:00' + (? * INTERVAL '1 minute'), TIMESTAMP '2026-11-10 10:00:00' + (? * INTERVAL '1 minute'))",
			id, author, original, type, title, content, visibility, deleted ? java.sql.Timestamp.valueOf("2026-11-10 11:00:00") : null, id, id);
	}

	void comment(long id, long post, long author, String content, boolean deleted) {
		jdbc.update("INSERT INTO comments(id, post_id, author_id, content, deleted_at, created_at, updated_at)"
			+ " VALUES (?, ?, ?, ?, ?, TIMESTAMP '2026-11-10 10:30:00', TIMESTAMP '2026-11-10 10:30:00')",
			id, post, author, content, deleted ? java.sql.Timestamp.valueOf("2026-11-10 11:00:00") : null);
	}

	static String email(long id) {
		return "admin-post" + id + "@test.local";
	}

	MockHttpServletRequestBuilder as(long id, String role, MockHttpServletRequestBuilder request) {
		return request.header("Authorization", "Bearer " + jwt.createAccessToken(email(id), role));
	}

	MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
		return as(ADMIN, "ADMIN", request);
	}

	String visibility(long id) {
		return jdbc.queryForObject("SELECT visibility FROM posts WHERE id = ?", String.class, id);
	}

	@Test
	void listsPostsAndCommentsExcludingDeleted() throws Exception {
		mvc.perform(asAdmin(get(ADMIN_POSTS))).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(4))
			.andExpect(jsonPath("$.items[0].id").value(OTHER))
			.andExpect(jsonPath("$.items[*].id", not(hasItem((int) DELETED))))
			.andExpect(jsonPath("$.items[1].type").value("REPOST"))
			.andExpect(jsonPath("$.items[1].title").value(nullValue()))
			.andExpect(jsonPath("$.items[3].authorTag").value("author"));
		mvc.perform(asAdmin(get(ADMIN_POSTS).param("visibility", "HIDDEN"))).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].id").value(HIDDEN));
		mvc.perform(asAdmin(get(ADMIN_POSTS).param("q", "100%"))).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].id").value(POST));
		mvc.perform(asAdmin(get(ADMIN_POSTS).param("target", "COMMENT"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].id").value(COMMENT))
			.andExpect(jsonPath("$.items[0].postId").value(POST))
			.andExpect(jsonPath("$.items[0].postTitle").value("스프링 질문"))
			.andExpect(jsonPath("$.items[0].authorNickname").value("독자"));
		mvc.perform(asAdmin(get(ADMIN_POSTS).param("target", "COMMENT").param("q", "없는말"))).andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(asAdmin(get(ADMIN_POSTS).param("target", "USER"))).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(get(ADMIN_POSTS).param("visibility", "PUBLIC"))).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(get(ADMIN_POSTS).param("target", "COMMENT").param("visibility", "VISIBLE"))).andExpect(status().isBadRequest());
		mvc.perform(as(READER, "USER", get(ADMIN_POSTS))).andExpect(status().isForbidden());
	}

	@Test
	void restrictionHidesFromOthersAndRestoreReturnsToVisible() throws Exception {
		mvc.perform(asAdmin(patch(ADMIN_POSTS + "/" + POST + "/visibility").contentType(MediaType.APPLICATION_JSON).content("{\"restricted\":true}")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("RESTRICTED"));
		assertEquals("RESTRICTED", visibility(POST));
		// 같은 값을 다시 보내도 200이다.
		mvc.perform(asAdmin(patch(ADMIN_POSTS + "/" + POST + "/visibility").contentType(MediaType.APPLICATION_JSON).content("{\"restricted\":true}")))
			.andExpect(status().isOk());
		// 다른 사람과 비로그인에게는 없는 글이고, 작성자는 상세를 본다. 퍼간 글은 원본을 안내만 한다.
		mvc.perform(get("/api/v1/feeds/" + POST)).andExpect(status().isNotFound());
		mvc.perform(as(READER, "USER", get("/api/v1/feeds/" + POST))).andExpect(status().isNotFound());
		mvc.perform(as(AUTHOR, "USER", get("/api/v1/feeds/" + POST))).andExpect(status().isOk())
			.andExpect(jsonPath("$.visibility").value("RESTRICTED")).andExpect(jsonPath("$.edited").value(false));
		mvc.perform(get("/api/v1/feeds")).andExpect(jsonPath("$.items[*].id", not(hasItem((int) POST))));
		mvc.perform(get("/api/v1/feeds/" + REPOST)).andExpect(jsonPath("$.original.available").value(false));

		mvc.perform(asAdmin(patch(ADMIN_POSTS + "/" + POST + "/visibility").contentType(MediaType.APPLICATION_JSON).content("{\"restricted\":false}")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("VISIBLE"));
		mvc.perform(get("/api/v1/feeds/" + POST)).andExpect(status().isOk());
		mvc.perform(get("/api/v1/feeds/" + REPOST)).andExpect(jsonPath("$.original.available").value(true));
	}

	@Test
	void adminDoesNotLiftAuthorHidingButCanRestrictIt() throws Exception {
		mvc.perform(asAdmin(patch(ADMIN_POSTS + "/" + HIDDEN + "/visibility").contentType(MediaType.APPLICATION_JSON).content("{\"restricted\":false}")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("HIDDEN"));
		mvc.perform(asAdmin(patch(ADMIN_POSTS + "/" + HIDDEN + "/visibility").contentType(MediaType.APPLICATION_JSON).content("{\"restricted\":true}")))
			.andExpect(jsonPath("$.visibility").value("RESTRICTED"));
	}

	@Test
	void deletesPostsAndCommentsLikeAuthorDeletion() throws Exception {
		mvc.perform(asAdmin(put(ADMIN_POSTS + "/" + COMMENT).param("target", "COMMENT").contentType(MediaType.APPLICATION_JSON)
			.content("{\"action\":\"DELETE\"}"))).andExpect(status().isNoContent());
		// 댓글 목록은 배열이다. 살아 있던 댓글은 하나뿐이라 목록이 빈다.
		mvc.perform(get("/api/v1/feeds/" + POST + "/comments")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
		mvc.perform(asAdmin(put(ADMIN_POSTS + "/" + POST).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"DELETE\"}")))
			.andExpect(status().isNoContent());
		assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM posts WHERE id = ?", java.sql.Timestamp.class, POST));
		mvc.perform(as(AUTHOR, "USER", get("/api/v1/feeds/" + POST))).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/feeds/" + REPOST)).andExpect(jsonPath("$.original.available").value(false));
		// 이미 삭제한 대상은 404다.
		mvc.perform(asAdmin(put(ADMIN_POSTS + "/" + POST).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"DELETE\"}")))
			.andExpect(status().isNotFound());
		mvc.perform(asAdmin(put(ADMIN_POSTS + "/" + DELETED_COMMENT).param("target", "COMMENT").contentType(MediaType.APPLICATION_JSON)
			.content("{\"action\":\"DELETE\"}"))).andExpect(status().isNotFound());
	}

	@Test
	void rejectsInvalidRequests() throws Exception {
		mvc.perform(asAdmin(put(ADMIN_POSTS + "/" + POST).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"HIDE\"}")))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("허용하지 않는 조치입니다."));
		mvc.perform(asAdmin(put(ADMIN_POSTS + "/" + POST).contentType(MediaType.APPLICATION_JSON).content("{}")))
			.andExpect(status().isBadRequest());
		mvc.perform(asAdmin(patch(ADMIN_POSTS + "/" + POST + "/visibility").contentType(MediaType.APPLICATION_JSON).content("{\"restricted\":\"true\"}")))
			.andExpect(status().isBadRequest());
		mvc.perform(asAdmin(patch(ADMIN_POSTS + "/" + POST + "/visibility").contentType(MediaType.APPLICATION_JSON).content("{}")))
			.andExpect(status().isBadRequest());
		mvc.perform(asAdmin(patch(ADMIN_POSTS + "/" + COMMENT + "/visibility").param("target", "COMMENT").contentType(MediaType.APPLICATION_JSON)
			.content("{\"restricted\":true}"))).andExpect(status().isBadRequest());
		mvc.perform(asAdmin(patch(ADMIN_POSTS + "/999/visibility").contentType(MediaType.APPLICATION_JSON).content("{\"restricted\":true}")))
			.andExpect(status().isNotFound());
		mvc.perform(as(AUTHOR, "USER", put(ADMIN_POSTS + "/" + OTHER).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"DELETE\"}")))
			.andExpect(status().isForbidden());
		assertEquals("VISIBLE", visibility(POST));
		assertNull(jdbc.queryForObject("SELECT deleted_at FROM posts WHERE id = ?", java.sql.Timestamp.class, OTHER));
	}
}
