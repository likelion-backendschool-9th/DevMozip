package com.devmozip.backend.feed;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;

import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 피드 API 테스트의 공통 준비. 시각을 고정하고 이미지는 테스트마다 비우는 임시 폴더(IMAGE_STORAGE)에 저장한다. */
abstract class FeedApiTestSupport {
	static final String FEEDS = "/api/v1/feeds";
	static final String COMMENTS = "/api/v1/comments";
	// 서울 시각 2026-11-10 10:15:00
	static final Instant NOW = Instant.parse("2026-11-10T01:15:00Z");
	static final Path IMAGES = temporaryFolder();
	// 1x1 WebP (VP8 손실 압축)
	static final byte[] WEBP = Base64.getDecoder().decode("UklGRiQAAABXRUJQVlA4IBgAAAAwAQCdASoBAAEAAwA0JaQAA3AA/vuUAAA=");

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper mapper;
	@Autowired JwtProvider jwt;
	@Autowired MemberRepository members;
	@MockitoBean RefreshTokenStore refreshTokens;
	@MockitoBean(name = "feedClock") Clock clock;
	final AtomicReference<Instant> now = new AtomicReference<>(NOW);
	int sequence;

	@DynamicPropertySource
	static void imageStorage(DynamicPropertyRegistry registry) {
		registry.add("IMAGE_STORAGE", IMAGES::toString);
	}

	@BeforeEach
	void prepare() throws IOException {
		now.set(NOW);
		when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
		when(clock.instant()).thenAnswer(invocation -> now.get());
		jdbc.update("DELETE FROM reports");
		jdbc.update("DELETE FROM likes");
		jdbc.update("DELETE FROM bookmarks");
		jdbc.update("DELETE FROM comments");
		jdbc.update("DELETE FROM posts WHERE original_post_id IS NOT NULL");
		jdbc.update("DELETE FROM posts");
		jdbc.update("DELETE FROM follows");
		jdbc.update("DELETE FROM users");
		try (Stream<Path> files = Files.list(IMAGES)) {
			for (Path file : files.toList()) Files.delete(file);
		}
	}

	private static Path temporaryFolder() {
		try {
			return Files.createTempDirectory("feed-images");
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	void advance(long seconds) {
		now.set(now.get().plusSeconds(seconds));
	}

	Member member(String name, Member.MemberStatus status) {
		sequence++;
		return members.save(Member.builder()
			.email(name + sequence + "@feed.test")
			.nickname(name + sequence)
			.tag(name + "-" + sequence)
			.role(Member.MemberRole.USER)
			.status(status)
			.jobCategories(List.of("백엔드 개발자"))
			.skills(List.of())
			.interests(List.of())
			.build());
	}

	Member member(String name) {
		return member(name, Member.MemberStatus.ACTIVE);
	}

	<B extends AbstractMockHttpServletRequestBuilder<B>> B as(Member who, B request) {
		return request.header("Authorization", "Bearer " + jwt.createAccessToken(who.getEmail(), "USER"));
	}

	static Map<String, Object> postBody(String category, String title, String content, List<String> stacks, List<String> interests) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("category", category);
		body.put("title", title);
		body.put("content", content);
		body.put("techStacks", stacks);
		body.put("interests", interests);
		return body;
	}

	static Map<String, Object> postBody(String title) {
		return postBody("INFO", title, "본문 " + title, List.of(), List.of());
	}

	MockMultipartHttpServletRequestBuilder multipartPost(HttpMethod method, String path, Object body, MockMultipartFile... images) {
		MockMultipartHttpServletRequestBuilder builder = multipart(method, path);
		builder.file(new MockMultipartFile("post", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsBytes(body)));
		for (MockMultipartFile image : images) builder.file(image);
		return builder;
	}

	ResultActions create(Member who, Object body, MockMultipartFile... images) throws Exception {
		return mvc.perform(as(who, multipartPost(HttpMethod.POST, FEEDS, body, images)));
	}

	ResultActions update(Member who, long id, Object body, MockMultipartFile... images) throws Exception {
		return mvc.perform(as(who, multipartPost(HttpMethod.PUT, FEEDS + "/" + id, body, images)));
	}

	long write(Member who, String title) throws Exception {
		return write(who, postBody(title));
	}

	long write(Member who, Object body) throws Exception {
		return json(create(who, body).andExpect(status().isCreated())).get("id").asLong();
	}

	JsonNode json(ResultActions actions) throws Exception {
		return mapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	JsonNode detail(Member who, long id) throws Exception {
		MockHttpServletRequestBuilder request = get(FEEDS + "/" + id);
		return json(mvc.perform(who == null ? request : as(who, request)).andExpect(status().isOk()));
	}

	List<Long> ids(JsonNode page) {
		List<Long> ids = new ArrayList<>();
		page.get("items").forEach(item -> ids.add(item.get("id").asLong()));
		return ids;
	}

	long count(String sql, Object... args) {
		Long value = jdbc.queryForObject(sql, Long.class, args);
		return value == null ? 0 : value;
	}

	List<String> storedFiles() throws IOException {
		try (Stream<Path> files = Files.list(IMAGES)) {
			return files.map(path -> path.getFileName().toString()).sorted().toList();
		}
	}

	static MockMultipartFile image(String format, String filename, String contentType) {
		return new MockMultipartFile("images", filename, contentType, imageBytes(format));
	}

	static MockMultipartFile png() {
		return image("png", "a.png", "image/png");
	}

	static byte[] imageBytes(String format) {
		if ("webp".equals(format)) return WEBP.clone();
		try {
			BufferedImage picture = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			if (!ImageIO.write(picture, format, output)) throw new IllegalStateException(format);
			return output.toByteArray();
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}
}
