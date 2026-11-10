package com.devmozip.backend.feed;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import com.devmozip.backend.entity.Follow;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.FollowRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;

/** WBS 8.1~8.6 게시글 API (docs/feed-plan.md 4절). PostgreSQL과 local(H2)에서 같은 동작을 확인한다. */
abstract class FeedPostApiTests extends FeedApiTestSupport {
	@Autowired FollowRepository follows;

	static List<String> distinctTags(int size) {
		return IntStream.range(0, size).mapToObj(index -> "tag" + index).toList();
	}

	@Test
	void anonymousCanReadButNotWrite() throws Exception {
		Member writer = member("writer");
		long id = write(writer, "공개 글");
		mvc.perform(get(FEEDS)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(id));
		mvc.perform(get(FEEDS + "/recommands")).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1));
		mvc.perform(get(FEEDS + "/" + id)).andExpect(status().isOk())
			.andExpect(jsonPath("$.mine").value(false)).andExpect(jsonPath("$.liked").value(false));
		mvc.perform(get(FEEDS + "/" + id + "/comments")).andExpect(status().isOk());
		// 만료·위조 토큰이 있어도 공개 조회는 비로그인으로 처리한다.
		mvc.perform(get(FEEDS).header("Authorization", "Bearer invalid")).andExpect(status().isOk());

		mvc.perform(get(FEEDS + "/following")).andExpect(status().isUnauthorized());
		mvc.perform(multipartPost(HttpMethod.POST, FEEDS, postBody("익명"))).andExpect(status().isUnauthorized());
		mvc.perform(multipartPost(HttpMethod.PUT, FEEDS + "/" + id, postBody("익명"))).andExpect(status().isUnauthorized());
		mvc.perform(post(FEEDS + "/" + id + "/delete")).andExpect(status().isUnauthorized());
		mvc.perform(patch(FEEDS + "/" + id + "/likes")).andExpect(status().isUnauthorized());
		assertEquals(1, count("SELECT count(*) FROM posts"));
	}

	@Test
	void createUsesTokenMemberAndStoresImagesWithPlannedNames() throws Exception {
		Member writer = member("writer");
		Member other = member("other");
		Map<String, Object> body = postBody("FEEDBACK", "  첫 API 설계  ", "  본문 그대로\n", List.of(" Spring Boot ", "타입스크립트"), List.of("교육"));
		body.put("authorId", other.getId());
		body.put("viewCount", 99);
		JsonNode created = json(create(writer, body, png(), image("jpg", "b.jpeg", "image/jpeg"), image("gif", "c.gif", "image/gif"))
			.andExpect(status().isCreated()));
		long id = created.get("id").asLong();

		assertEquals("POST", created.get("type").asString());
		assertEquals("FEEDBACK", created.get("category").asString());
		assertEquals("첫 API 설계", created.get("title").asString());
		assertEquals("  본문 그대로\n", created.get("content").asString());
		assertEquals(writer.getId(), created.get("author").get("id").asLong());
		assertEquals(writer.getTag(), created.get("author").get("tag").asString());
		assertEquals(0, created.get("viewCount").asLong());
		assertEquals("2026-11-10T10:15:00+09:00", created.get("createdAt").asString());
		assertFalse(created.get("edited").asBoolean());
		assertTrue(created.get("mine").asBoolean());
		List<String> urls = new ArrayList<>();
		created.get("imageUrls").forEach(url -> urls.add(url.asString()));
		String prefix = "/images/feed_" + id + "_20261110101500_";
		assertEquals(List.of(prefix + "1.png", prefix + "2.jpg", prefix + "3.gif"), urls);
		assertEquals(urls.stream().map(url -> url.substring("/images/".length())).sorted().toList(), storedFiles());
		List<String> stacks = new ArrayList<>();
		created.get("techStacks").forEach(stack -> stacks.add(stack.asString()));
		assertEquals(List.of("Spring Boot", "타입스크립트"), stacks);
		assertEquals(writer.getId(), count("SELECT author_id FROM posts WHERE id = ?", id));

		mvc.perform(get(urls.get(0))).andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/png"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
		mvc.perform(get("/images/secret.txt")).andExpect(status().isNotFound());
		mvc.perform(get("/images/feed_" + id + "_missing.png")).andExpect(status().isNotFound());
	}

	@Test
	void acceptsWebpByContent() throws Exception {
		Member writer = member("writer");
		JsonNode created = json(create(writer, postBody("웹피"), new MockMultipartFile("images", "x.png", "image/png", WEBP))
			.andExpect(status().isCreated()));
		assertTrue(created.get("imageUrls").get(0).asString().endsWith("_1.webp"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"title-empty", "title-long", "content-blank", "content-long", "category", "category-missing",
		"stack-duplicate", "stack-long", "stack-many", "interest-blank"})
	void rejectsInvalidPostWithoutSavingAnything(String kind) throws Exception {
		Member writer = member("writer");
		Map<String, Object> body = postBody("정상 제목");
		switch (kind) {
			case "title-empty" -> body.put("title", "   ");
			case "title-long" -> body.put("title", "가".repeat(151));
			case "content-blank" -> body.put("content", " \n ");
			case "content-long" -> body.put("content", "a".repeat(10_001));
			case "category" -> body.put("category", "정보");
			case "category-missing" -> body.remove("category");
			case "stack-duplicate" -> body.put("techStacks", List.of("React", "react "));
			case "stack-long" -> body.put("techStacks", List.of("a".repeat(31)));
			case "stack-many" -> body.put("techStacks", distinctTags(31));
			case "interest-blank" -> body.put("interests", List.of(" "));
			default -> fail(kind);
		}
		create(writer, body, png()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString());
		assertEquals(0, count("SELECT count(*) FROM posts"));
		assertEquals(List.of(), storedFiles());
	}

	@Test
	void acceptsBoundaryLengths() throws Exception {
		Member writer = member("writer");
		create(writer, postBody("INFO", "가".repeat(150), "a".repeat(10_000), distinctTags(30), List.of("b".repeat(30))))
			.andExpect(status().isCreated());
	}

	@Test
	void rejectsInvalidImagesWithoutSavingPost() throws Exception {
		Member writer = member("writer");
		create(writer, postBody("넷"), png(), png(), png(), png()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("이미지는 PNG/JPEG/WebP/GIF, 각 512KB 이하, 최대 3장까지 첨부할 수 있습니다."));
		create(writer, postBody("텍스트"), new MockMultipartFile("images", "a.png", "image/png", "not an image".getBytes()))
			.andExpect(status().isBadRequest());
		byte[] large = new byte[512 * 1024 + 1];
		byte[] header = imageBytes("png");
		System.arraycopy(header, 0, large, 0, header.length);
		create(writer, postBody("큰 파일"), new MockMultipartFile("images", "a.png", "image/png", large)).andExpect(status().isBadRequest());
		create(writer, postBody("빈 파일"), new MockMultipartFile("images", "a.png", "image/png", new byte[0])).andExpect(status().isBadRequest());
		Map<String, Object> keep = postBody("남길 이미지");
		keep.put("keepImageUrls", List.of("/images/feed_1_20261110101500_1.png"));
		create(writer, keep).andExpect(status().isBadRequest());
		// JSON 파트 "post" 없이 이미지만 보냄
		mvc.perform(as(writer, multipart(FEEDS).file(png()))).andExpect(status().isBadRequest());
		assertEquals(0, count("SELECT count(*) FROM posts"));
		assertEquals(List.of(), storedFiles());
	}

	@Test
	void hiddenRestrictedAndDeletedPostsAreOnlyVisibleToAuthorUntilDeleted() throws Exception {
		Member writer = member("writer");
		Member reader = member("reader");
		long hidden = write(writer, "숨김");
		long restricted = write(writer, "관리자 숨김");
		jdbc.update("UPDATE posts SET visibility = 'HIDDEN' WHERE id = ?", hidden);
		jdbc.update("UPDATE posts SET visibility = 'RESTRICTED' WHERE id = ?", restricted);
		for (long id : new long[]{hidden, restricted}) {
			mvc.perform(get(FEEDS + "/" + id)).andExpect(status().isNotFound());
			mvc.perform(as(reader, get(FEEDS + "/" + id))).andExpect(status().isNotFound());
			mvc.perform(get(FEEDS + "/" + id + "/comments")).andExpect(status().isNotFound());
			assertTrue(detail(writer, id).get("mine").asBoolean());
		}
		assertEquals("HIDDEN", detail(writer, hidden).get("visibility").asString());
		assertEquals(List.of(), ids(json(mvc.perform(as(writer, get(FEEDS))))));

		mvc.perform(as(writer, post(FEEDS + "/" + hidden + "/delete"))).andExpect(status().isNoContent());
		mvc.perform(as(writer, get(FEEDS + "/" + hidden))).andExpect(status().isNotFound());
		mvc.perform(get(FEEDS + "/999999")).andExpect(status().isNotFound());
		mvc.perform(get(FEEDS + "/abc")).andExpect(status().isUnauthorized());
	}

	@Test
	void postsOfSuspendedAuthorsAreHidden() throws Exception {
		Member writer = member("writer");
		long id = write(writer, "정지 전 글");
		jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", writer.getId());
		mvc.perform(get(FEEDS + "/" + id)).andExpect(status().isNotFound());
		assertEquals(List.of(), ids(json(mvc.perform(get(FEEDS)))));
	}

	@Test
	void viewCountIncreasesForOthersOnlyWhenRequested() throws Exception {
		Member writer = member("writer");
		Member reader = member("reader");
		long id = write(writer, "조회");
		assertEquals(0, detail(reader, id).get("viewCount").asLong());
		assertEquals(1, json(mvc.perform(as(reader, get(FEEDS + "/" + id).param("view", "true")))).get("viewCount").asLong());
		// 서버는 방문 기록을 저장하지 않으므로 같은 사람이 다시 요청해도 오른다. 처음 읽는지는 화면이 정한다.
		assertEquals(2, json(mvc.perform(as(reader, get(FEEDS + "/" + id).param("view", "true")))).get("viewCount").asLong());
		assertEquals(3, json(mvc.perform(get(FEEDS + "/" + id).param("view", "true"))).get("viewCount").asLong());
		assertEquals(3, json(mvc.perform(as(writer, get(FEEDS + "/" + id).param("view", "true")))).get("viewCount").asLong());
		// Spring은 yes·on·1도 true로 읽는다. 그 밖의 값은 400이다.
		mvc.perform(get(FEEDS + "/" + id).param("view", "maybe")).andExpect(status().isBadRequest());

		jdbc.update("UPDATE posts SET visibility = 'HIDDEN' WHERE id = ?", id);
		mvc.perform(as(reader, get(FEEDS + "/" + id).param("view", "true"))).andExpect(status().isNotFound());
		assertEquals(3, count("SELECT view_count FROM posts WHERE id = ?", id));
	}

	@Test
	void onlyAuthorUpdatesAndImagesAreKeptOrReplaced() throws Exception {
		Member writer = member("writer");
		Member other = member("other");
		JsonNode created = json(create(writer, postBody("원래 제목"), png(), image("jpg", "b.jpg", "image/jpeg")).andExpect(status().isCreated()));
		long id = created.get("id").asLong();
		String first = created.get("imageUrls").get(0).asString();
		String second = created.get("imageUrls").get(1).asString();

		update(other, id, postBody("남의 수정")).andExpect(status().isForbidden());
		Map<String, Object> wrongKeep = postBody("잘못된 이미지");
		wrongKeep.put("keepImageUrls", List.of("/images/feed_" + id + "_20990101000000_1.png"));
		update(writer, id, wrongKeep).andExpect(status().isBadRequest());
		Map<String, Object> tooMany = postBody("너무 많음");
		tooMany.put("keepImageUrls", List.of(first, second));
		update(writer, id, tooMany, png(), png()).andExpect(status().isBadRequest());

		advance(60);
		Map<String, Object> body = postBody("COMMUNITY", "바뀐 제목", "바뀐 본문", List.of("Java"), List.of());
		body.put("keepImageUrls", List.of(second));
		JsonNode updated = json(update(writer, id, body, image("gif", "c.gif", "image/gif")).andExpect(status().isOk()));
		assertEquals("바뀐 제목", updated.get("title").asString());
		assertEquals("COMMUNITY", updated.get("category").asString());
		assertTrue(updated.get("edited").asBoolean());
		assertEquals("2026-11-10T10:16:00+09:00", updated.get("updatedAt").asString());
		assertEquals("2026-11-10T10:15:00+09:00", updated.get("createdAt").asString());
		assertEquals(second, updated.get("imageUrls").get(0).asString());
		assertEquals("/images/feed_" + id + "_20261110101600_2.gif", updated.get("imageUrls").get(1).asString());
		assertEquals(2, updated.get("imageUrls").size());
		// 빠진 이미지 파일은 커밋 뒤에 지운다.
		assertFalse(storedFiles().contains(first.substring("/images/".length())));
		assertTrue(storedFiles().contains(second.substring("/images/".length())));
		assertEquals(2, storedFiles().size());
		assertEquals("VISIBLE", updated.get("visibility").asString());

		// 수정은 공개 범위를 바꾸지 않는다(작성자 숨김은 WBS 3.8).
		jdbc.update("UPDATE posts SET visibility = 'HIDDEN' WHERE id = ?", id);
		Map<String, Object> withVisibility = postBody("공개로 바꾸기");
		withVisibility.put("visibility", "VISIBLE");
		update(writer, id, withVisibility).andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("HIDDEN"));
		update(other, id, postBody("숨긴 글 수정")).andExpect(status().isNotFound());
	}

	@Test
	void deleteIsSoftAndRemovesImageFiles() throws Exception {
		Member writer = member("writer");
		Member other = member("other");
		long id = json(create(writer, postBody("지울 글"), png()).andExpect(status().isCreated())).get("id").asLong();
		mvc.perform(as(other, post(FEEDS + "/" + id + "/delete"))).andExpect(status().isForbidden());
		assertEquals(1, storedFiles().size());

		mvc.perform(as(writer, post(FEEDS + "/" + id + "/delete"))).andExpect(status().isNoContent());
		assertEquals(1, count("SELECT count(*) FROM posts WHERE id = ? AND deleted_at IS NOT NULL", id));
		assertEquals(List.of(), storedFiles());
		mvc.perform(get(FEEDS + "/" + id)).andExpect(status().isNotFound());
		mvc.perform(as(writer, post(FEEDS + "/" + id + "/delete"))).andExpect(status().isNotFound());
		update(writer, id, postBody("삭제 뒤 수정")).andExpect(status().isNotFound());
		assertEquals(List.of(), ids(json(mvc.perform(get(FEEDS)))));
	}

	@Test
	void listsFilterSortAndPage() throws Exception {
		Member writer = member("writer");
		Member reader = member("reader");
		long info = write(writer, postBody("INFO", "React 정리", "컴포넌트", List.of("TypeScript"), List.of()));
		advance(60);
		long feedback = write(writer, postBody("FEEDBACK", "API 리뷰", "응답 구조", List.of("Spring Boot"), List.of("교육")));
		advance(60);
		long community = write(reader, postBody("COMMUNITY", "주말 회고", "작은 성취", List.of(), List.of()));
		jdbc.update("UPDATE posts SET view_count = 10 WHERE id = ?", info);

		assertEquals(List.of(community, feedback, info), ids(json(mvc.perform(get(FEEDS)))));
		assertEquals(List.of(info, community, feedback), ids(json(mvc.perform(get(FEEDS).param("sort", "views")))));
		assertEquals(List.of(feedback), ids(json(mvc.perform(get(FEEDS).param("category", "FEEDBACK")))));
		assertEquals(List.of(feedback), ids(json(mvc.perform(get(FEEDS + "/recommands").param("q", "spring 교육")))));
		assertEquals(List.of(info), ids(json(mvc.perform(get(FEEDS).param("q", " typescript ")))));

		JsonNode first = json(mvc.perform(get(FEEDS).param("size", "2")));
		assertEquals(List.of(community, feedback), ids(first));
		assertTrue(first.get("hasNext").asBoolean());
		assertEquals(3, first.get("totalCount").asLong());
		assertEquals(0, first.get("page").asInt());
		JsonNode second = json(mvc.perform(get(FEEDS).param("size", "2").param("page", "1")));
		assertEquals(List.of(info), ids(second));
		assertFalse(second.get("hasNext").asBoolean());

		List<Long> recommended = ids(json(mvc.perform(get(FEEDS + "/recommands").param("seed", "42"))));
		assertEquals(recommended, ids(json(mvc.perform(get(FEEDS + "/recommands").param("seed", "42")))));
		assertEquals(3, recommended.size());

		for (String[] bad : new String[][]{{"sort", "oldest"}, {"category", "정보"}, {"page", "-1"}, {"size", "0"},
			{"size", "101"}, {"seed", "-1"}, {"page", "x"}, {"q", "a".repeat(101)}}) {
			mvc.perform(get(FEEDS).param(bad[0], bad[1])).andExpect(status().isBadRequest());
		}
		mvc.perform(get(FEEDS).param("size", "100")).andExpect(status().isOk());
	}

	@Test
	void followingListShowsOnlyFollowedAuthorsWithFilters() throws Exception {
		Member me = member("me");
		Member followed = member("followed");
		Member other = member("other");
		long info = write(followed, postBody("INFO", "정보 글", "본문", List.of(), List.of()));
		advance(60);
		long feedback = write(followed, postBody("FEEDBACK", "피드백 글", "본문", List.of(), List.of()));
		write(other, "모르는 사람 글");
		write(me, "내 글");
		follows.save(Follow.create(me, followed, java.time.LocalDateTime.of(2026, 11, 10, 9, 0)));

		assertEquals(List.of(feedback, info), ids(json(mvc.perform(as(me, get(FEEDS + "/following"))))));
		assertEquals(List.of(info), ids(json(mvc.perform(as(me, get(FEEDS + "/following").param("category", "INFO"))))));
		assertEquals(List.of(), ids(json(mvc.perform(as(other, get(FEEDS + "/following"))))));
		// 요청 값의 사용자 ID는 쓰지 않는다.
		assertEquals(List.of(), ids(json(mvc.perform(as(other, get(FEEDS + "/following").param("userId", String.valueOf(me.getId())))))));
	}

	@Test
	void repostsAreNotListedInFeedButHaveDetail() throws Exception {
		Member writer = member("writer");
		Member reader = member("reader");
		long original = write(writer, "원본");
		long repost = json(mvc.perform(as(reader, post(FEEDS + "/" + original + "/reposts")))
			.andExpect(status().isCreated())).get("id").asLong();
		assertEquals(List.of(original), ids(json(mvc.perform(get(FEEDS)))));
		JsonNode detail = detail(null, repost);
		assertEquals("REPOST", detail.get("type").asString());
		assertEquals(original, detail.get("original").get("id").asLong());
		assertTrue(detail.get("original").get("available").asBoolean());
		assertEquals("원본", detail.get("original").get("title").asString());
	}
}
