package com.devmozip.backend.feed;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.devmozip.backend.entity.Member;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/** WBS 8.7~8.13, 8.15~8.16 반응·댓글·퍼가기·신고 API. PostgreSQL과 local(H2)에서 같은 동작을 확인한다. */
abstract class FeedInteractionApiTests extends FeedApiTestSupport {
	ResultActions send(Member who, MockHttpServletRequestBuilder request, Object body) throws Exception {
		MockHttpServletRequestBuilder withBody = body == null ? request
			: request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
		return mvc.perform(who == null ? withBody : as(who, withBody));
	}

	long comment(Member who, long postId, String content) throws Exception {
		return json(send(who, post(FEEDS + "/" + postId + "/comments"), Map.of("content", content)).andExpect(status().isCreated()))
			.get("id").asLong();
	}

	// ---- 8.7 좋아요 ----

	@Test
	void likeTogglesAndAnswersFinalState() throws Exception {
		Member writer = member("writer");
		Member reader = member("reader");
		Member another = member("another");
		long id = write(writer, "좋아요");
		send(reader, patch(FEEDS + "/" + id + "/likes"), null).andExpect(status().isOk())
			.andExpect(jsonPath("$.postId").value(id)).andExpect(jsonPath("$.liked").value(true)).andExpect(jsonPath("$.likeCount").value(1));
		send(another, patch(FEEDS + "/" + id + "/likes"), null).andExpect(jsonPath("$.likeCount").value(2));
		JsonNode seen = detail(reader, id);
		assertTrue(seen.get("liked").asBoolean());
		assertEquals(2, seen.get("likeCount").asLong());
		assertFalse(detail(writer, id).get("liked").asBoolean());

		// 같은 토글이 두 번 처리되면 원래 상태로 돌아간다.
		send(reader, patch(FEEDS + "/" + id + "/likes"), null).andExpect(jsonPath("$.liked").value(false)).andExpect(jsonPath("$.likeCount").value(1));
		assertEquals(1, count("SELECT count(*) FROM likes WHERE post_id = ? AND project_id IS NULL", id));
		// 요청 값의 사용자 ID는 쓰지 않는다.
		send(reader, patch(FEEDS + "/" + id + "/likes").param("userId", String.valueOf(another.getId())), null)
			.andExpect(jsonPath("$.liked").value(true));
		assertEquals(1, count("SELECT count(*) FROM likes WHERE user_id = ?", reader.getId()));
		assertEquals(1, count("SELECT count(*) FROM likes WHERE user_id = ?", another.getId()));
	}

	@Test
	void popularSortUsesLikeCount() throws Exception {
		Member writer = member("writer");
		Member reader = member("reader");
		long older = write(writer, "오래된 인기 글");
		advance(60);
		long newer = write(writer, "새 글");
		send(reader, patch(FEEDS + "/" + older + "/likes"), null).andExpect(status().isOk());
		assertEquals(List.of(older, newer), ids(json(mvc.perform(get(FEEDS).param("sort", "popular")))));
		assertEquals(List.of(newer, older), ids(json(mvc.perform(get(FEEDS).param("sort", "latest")))));
	}

	@Test
	void reactionsNeedVisiblePostButBookmarkCanBeRemovedAnytime() throws Exception {
		Member writer = member("writer");
		Member reader = member("reader");
		long id = write(writer, "숨길 글");
		send(reader, post(FEEDS + "/" + id + "/bookmark"), null).andExpect(status().isOk()).andExpect(jsonPath("$.bookmarked").value(true));
		send(reader, post(FEEDS + "/" + id + "/bookmark"), null).andExpect(status().isOk()).andExpect(jsonPath("$.bookmarked").value(true));
		assertEquals(1, count("SELECT count(*) FROM bookmarks WHERE post_id = ? AND project_id IS NULL", id));
		assertTrue(detail(reader, id).get("bookmarked").asBoolean());

		jdbc.update("UPDATE posts SET visibility = 'HIDDEN' WHERE id = ?", id);
		send(reader, patch(FEEDS + "/" + id + "/likes"), null).andExpect(status().isNotFound());
		send(reader, post(FEEDS + "/" + id + "/bookmark"), null).andExpect(status().isNotFound());
		// 작성자는 자기 숨긴 글에도 반응할 수 있다(목업과 같다).
		send(writer, patch(FEEDS + "/" + id + "/likes"), null).andExpect(status().isOk());

		for (int attempt = 0; attempt < 2; attempt++) {
			send(reader, delete(FEEDS + "/" + id + "/bookmark"), null).andExpect(status().isOk()).andExpect(jsonPath("$.bookmarked").value(false));
		}
		assertEquals(0, count("SELECT count(*) FROM bookmarks"));
		send(null, post(FEEDS + "/" + id + "/bookmark"), null).andExpect(status().isUnauthorized());
		send(reader, patch(FEEDS + "/999999/likes"), null).andExpect(status().isNotFound());
	}

	// ---- 8.9~8.12 댓글 ----

	@Test
	void commentsAreListedCreatedEditedAndDeletedByAuthorOnly() throws Exception {
		Member writer = member("writer");
		Member reader = member("reader");
		long postId = write(writer, "댓글 글");
		long first = comment(reader, postId, "  첫 댓글  ");
		advance(60);
		long second = comment(writer, postId, "작성자 댓글");

		JsonNode list = json(send(null, get(FEEDS + "/" + postId + "/comments"), null).andExpect(status().isOk()));
		assertEquals(2, list.size());
		assertEquals(first, list.get(0).get("id").asLong());
		assertEquals("첫 댓글", list.get(0).get("content").asString());
		assertEquals(reader.getTag(), list.get(0).get("author").get("tag").asString());
		assertFalse(list.get(0).get("mine").asBoolean());
		assertEquals(second, list.get(1).get("id").asLong());
		assertTrue(json(send(reader, get(FEEDS + "/" + postId + "/comments"), null)).get(0).get("mine").asBoolean());
		assertEquals(2, detail(null, postId).get("commentCount").asLong());

		send(writer, put(COMMENTS + "/" + first), Map.of("content", "남의 수정")).andExpect(status().isForbidden());
		send(writer, delete(COMMENTS + "/" + first), null).andExpect(status().isForbidden());
		advance(60);
		send(reader, put(COMMENTS + "/" + first), Map.of("content", " 고친 댓글 ")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content").value("고친 댓글")).andExpect(jsonPath("$.edited").value(true))
			.andExpect(jsonPath("$.updatedAt").value("2026-11-10T10:17:00+09:00"));
		send(reader, delete(COMMENTS + "/" + first), null).andExpect(status().isNoContent());
		assertEquals(1, count("SELECT count(*) FROM comments WHERE id = ? AND deleted_at IS NOT NULL", first));
		assertEquals(1, json(send(null, get(FEEDS + "/" + postId + "/comments"), null)).size());
		assertEquals(1, detail(null, postId).get("commentCount").asLong());
		send(reader, put(COMMENTS + "/" + first), Map.of("content", "삭제 뒤 수정")).andExpect(status().isNotFound());
		send(null, post(FEEDS + "/" + postId + "/comments"), Map.of("content", "익명")).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   "})
	void rejectsEmptyComments(String content) throws Exception {
		Member writer = member("writer");
		long postId = write(writer, "댓글 검증");
		send(writer, post(FEEDS + "/" + postId + "/comments"), Map.of("content", content)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("댓글은 1~1,000자로 입력해 주세요."));
	}

	@Test
	void commentLengthLimitIsThousandCharacters() throws Exception {
		Member writer = member("writer");
		long postId = write(writer, "댓글 길이");
		send(writer, post(FEEDS + "/" + postId + "/comments"), Map.of("content", "a".repeat(1000))).andExpect(status().isCreated());
		send(writer, post(FEEDS + "/" + postId + "/comments"), Map.of("content", "a".repeat(1001))).andExpect(status().isBadRequest());
		send(writer, post(FEEDS + "/" + postId + "/comments"), Map.of("content", 123)).andExpect(status().isBadRequest());
	}

	@Test
	void commentsOfDeletedOrHiddenPostsAreNotFound() throws Exception {
		Member writer = member("writer");
		Member reader = member("reader");
		long postId = write(writer, "곧 지울 글");
		long commentId = comment(reader, postId, "남은 댓글");
		send(writer, post(FEEDS + "/" + postId + "/delete"), null).andExpect(status().isNoContent());

		send(null, get(FEEDS + "/" + postId + "/comments"), null).andExpect(status().isNotFound());
		send(reader, post(FEEDS + "/" + postId + "/comments"), Map.of("content", "새 댓글")).andExpect(status().isNotFound());
		send(reader, put(COMMENTS + "/" + commentId), Map.of("content", "수정")).andExpect(status().isNotFound());
		send(reader, delete(COMMENTS + "/" + commentId), null).andExpect(status().isNotFound());
		send(writer, post(COMMENTS + "/" + commentId + "/reports"), Map.of("reason", "기타")).andExpect(status().isNotFound());

		long hidden = write(writer, "숨긴 글");
		long hiddenComment = comment(reader, hidden, "숨기기 전 댓글");
		jdbc.update("UPDATE posts SET visibility = 'HIDDEN' WHERE id = ?", hidden);
		send(reader, put(COMMENTS + "/" + hiddenComment), Map.of("content", "수정")).andExpect(status().isNotFound());
		send(writer, get(FEEDS + "/" + hidden + "/comments"), null).andExpect(status().isOk());
	}

	// ---- 8.13 퍼가기 ----

	@Test
	void repostOncePerOriginalAndRepostAgainAfterCancel() throws Exception {
		Member writer = member("writer");
		Member reader = member("reader");
		long original = json(create(writer, postBody("PROMO", "원본 제목", "원본 본문", List.of("React"), List.of()), png())
			.andExpect(status().isCreated())).get("id").asLong();

		JsonNode repost = json(send(reader, post(FEEDS + "/" + original + "/reposts"), Map.of("content", "좋은 글이에요"))
			.andExpect(status().isCreated()));
		long repostId = repost.get("id").asLong();
		assertEquals("REPOST", repost.get("type").asString());
		assertEquals("PROMO", repost.get("category").asString());
		assertTrue(repost.get("title").isNull());
		assertEquals("좋은 글이에요", repost.get("content").asString());
		assertEquals(0, repost.get("imageUrls").size());
		assertEquals(0, repost.get("techStacks").size());
		assertEquals(original, repost.get("original").get("id").asLong());
		assertEquals(writer.getTag(), repost.get("original").get("author").get("tag").asString());
		assertEquals(original, count("SELECT original_post_id FROM posts WHERE id = ?", repostId));

		send(reader, post(FEEDS + "/" + original + "/reposts"), Map.of("content", "또 퍼가기")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 내 프로필에 퍼간 글입니다."));
		JsonNode seen = detail(reader, original);
		assertEquals(repostId, seen.get("myRepost").get("id").asLong());
		assertEquals("좋은 글이에요", seen.get("myRepost").get("content").asString());
		assertTrue(detail(writer, original).get("myRepost").isNull());

		// 의견 수정은 퍼간 글의 id로 한다. 퍼간 글은 이미지를 받지 않는다.
		update(reader, repostId, Map.of("content", "의견을 고쳤어요")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content").value("의견을 고쳤어요")).andExpect(jsonPath("$.type").value("REPOST"));
		update(reader, repostId, Map.of("content", "이미지"), png()).andExpect(status().isBadRequest());
		update(reader, repostId, Map.of("content", "a".repeat(1001))).andExpect(status().isBadRequest());
		update(writer, repostId, Map.of("content", "남의 의견")).andExpect(status().isForbidden());

		for (int attempt = 0; attempt < 2; attempt++) {
			send(reader, delete(FEEDS + "/" + original + "/reposts"), null).andExpect(status().isNoContent());
		}
		assertEquals(1, count("SELECT count(*) FROM posts WHERE id = ? AND deleted_at IS NOT NULL", repostId));
		assertTrue(detail(reader, original).get("myRepost").isNull());
		send(reader, post(FEEDS + "/" + original + "/reposts"), null).andExpect(status().isCreated())
			.andExpect(jsonPath("$.content").isEmpty());
		// 본인 글도 퍼갈 수 있다(목업과 같다).
		send(writer, post(FEEDS + "/" + original + "/reposts"), Map.of()).andExpect(status().isCreated());
	}

	@Test
	void cannotRepostUnavailableOriginalsAndRepostsSurviveOriginalDeletion() throws Exception {
		Member writer = member("writer");
		Member reader = member("reader");
		Member another = member("another");
		long original = write(writer, "원본");
		long repostId = json(send(reader, post(FEEDS + "/" + original + "/reposts"), null).andExpect(status().isCreated())).get("id").asLong();
		// 퍼간 글은 다시 퍼갈 수 없다.
		send(another, post(FEEDS + "/" + repostId + "/reposts"), null).andExpect(status().isNotFound());
		send(another, post(FEEDS + "/" + original + "/reposts"), Map.of("content", "a".repeat(1001))).andExpect(status().isBadRequest());

		jdbc.update("UPDATE posts SET visibility = 'RESTRICTED' WHERE id = ?", original);
		send(another, post(FEEDS + "/" + original + "/reposts"), null).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("원본 글을 찾을 수 없습니다."));
		assertFalse(detail(reader, repostId).get("original").get("available").asBoolean());

		jdbc.update("UPDATE posts SET visibility = 'VISIBLE' WHERE id = ?", original);
		send(writer, post(FEEDS + "/" + original + "/delete"), null).andExpect(status().isNoContent());
		send(another, post(FEEDS + "/" + original + "/reposts"), null).andExpect(status().isNotFound());
		JsonNode survived = detail(reader, repostId);
		assertFalse(survived.get("original").get("available").asBoolean());
		assertEquals(original, survived.get("original").get("id").asLong());
		assertTrue(survived.get("original").get("title").isNull());
		// 원본이 삭제돼도 내가 퍼간 글은 정리할 수 있다.
		send(reader, delete(FEEDS + "/" + original + "/reposts"), null).andExpect(status().isNoContent());
		send(reader, get(FEEDS + "/" + repostId), null).andExpect(status().isNotFound());
	}

	// ---- 8.15·8.16 신고 ----

	@Test
	void reportsPostAndCommentOncePerAccount() throws Exception {
		Member writer = member("writer");
		Member reader = member("reader");
		long postId = write(writer, "신고 대상");
		long commentId = comment(writer, postId, "신고할 댓글");

		JsonNode report = json(send(reader, post(FEEDS + "/" + postId + "/reports"), Map.of("reason", "허위 정보"))
			.andExpect(status().isCreated()));
		assertEquals("POST", report.get("targetType").asString());
		assertEquals(postId, report.get("targetId").asLong());
		assertEquals("AWATING", report.get("status").asString());
		assertEquals("2026-11-10T10:15:00+09:00", report.get("createdAt").asString());
		send(reader, post(FEEDS + "/" + postId + "/reports"), Map.of("reason", "기타")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 신고한 내용입니다."));
		assertTrue(detail(reader, postId).get("reported").asBoolean());
		assertFalse(detail(writer, postId).get("reported").asBoolean());

		send(reader, post(COMMENTS + "/" + commentId + "/reports"), Map.of("reason", "욕설 또는 혐오 표현"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.targetType").value("COMMENT"));
		send(reader, post(COMMENTS + "/" + commentId + "/reports"), Map.of("reason", "기타")).andExpect(status().isConflict());
		assertTrue(json(send(reader, get(FEEDS + "/" + postId + "/comments"), null)).get(0).get("reported").asBoolean());

		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM reports WHERE comment_id = ?", commentId);
		assertNull(column(row, "post_id"));
		assertNull(column(row, "reported_user_id"));
		assertNull(column(row, "project_id"));
		assertEquals("욕설 또는 혐오 표현", column(row, "reason"));
		assertEquals("AWATING", column(row, "status"));
		assertEquals(reader.getId(), ((Number) column(row, "reporter_id")).longValue());
		assertEquals(2, count("SELECT count(*) FROM reports"));
	}

	@Test
	void rejectsOwnContentInvalidReasonsAndHiddenTargets() throws Exception {
		Member writer = member("writer");
		Member reader = member("reader");
		long postId = write(writer, "내 글");
		long commentId = comment(reader, postId, "내 댓글");
		send(writer, post(FEEDS + "/" + postId + "/reports"), Map.of("reason", "기타")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("본인이 작성한 내용은 신고할 수 없습니다."));
		send(reader, post(COMMENTS + "/" + commentId + "/reports"), Map.of("reason", "기타")).andExpect(status().isBadRequest());
		for (String reason : new String[]{"사칭", "", "스팸"}) {
			send(reader, post(FEEDS + "/" + postId + "/reports"), Map.of("reason", reason)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("신고 사유를 선택해 주세요."));
		}
		jdbc.update("UPDATE posts SET visibility = 'HIDDEN' WHERE id = ?", postId);
		send(reader, post(FEEDS + "/" + postId + "/reports"), Map.of("reason", "기타")).andExpect(status().isNotFound());
		send(null, post(FEEDS + "/" + postId + "/reports"), Map.of("reason", "기타")).andExpect(status().isUnauthorized());
		assertEquals(0, count("SELECT count(*) FROM reports"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"스팸 또는 광고", "욕설 또는 혐오 표현", "허위 정보", "저작권 침해", "기타"})
	void acceptsEveryMockReason(String reason) throws Exception {
		Member writer = member("writer");
		Member reader = member("reader");
		long postId = write(writer, "사유 " + reason);
		send(reader, post(FEEDS + "/" + postId + "/reports"), Map.of("reason", reason)).andExpect(status().isCreated());
	}

	// PostgreSQL은 컬럼 이름을 소문자로, H2는 대문자로 돌려준다.
	static Object column(Map<String, Object> row, String name) {
		return row.containsKey(name) ? row.get(name) : row.get(name.toUpperCase());
	}
}
