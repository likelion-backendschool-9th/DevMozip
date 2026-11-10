package com.devmozip.backend.message;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 메신저 API 테스트의 공통 준비. 시각을 고정해 저장 값과 같은 시각의 전송을 확인한다. */
abstract class MessengerApiTestSupport {
	static final String CHATS = "/api/v1/messages/chats";
	// 서울 시각으로 2026-11-06 10:15:00이다. UTC로 저장되면 01:15가 된다.
	static final Instant NOW = Instant.parse("2026-11-06T01:15:00Z");
	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper mapper;
	@Autowired JwtProvider jwt;
	@Autowired MemberRepository members;
	@MockitoBean(name = "messengerClock") Clock clock;

	@BeforeEach void fixClock() {
		when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
		when(clock.instant()).thenReturn(NOW);
	}

	Member member(String name) {
		int number = SEQUENCE.incrementAndGet();
		return members.save(Member.builder()
			.email(name + number + "@devmozip.test")
			.nickname(name + number)
			.tag(name + "_" + number)
			.role(Member.MemberRole.USER)
			.status(Member.MemberStatus.ACTIVE)
			.collaborationStatus(Member.CollaborationStatus.AVAILABLE)
			.jobCategories(List.of("백엔드 개발자"))
			.build());
	}

	void suspend(Member member) {
		jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", member.getId());
	}

	ResultActions getAs(Member who, String path) throws Exception {
		return mvc.perform(withToken(get(path), who));
	}

	ResultActions postAs(Member who, String path, Object body) throws Exception {
		return mvc.perform(withToken(post(path), who).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
	}

	ResultActions patchAs(Member who, String path, Object body) throws Exception {
		return mvc.perform(withToken(patch(path), who).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
	}

	ResultActions postRawAs(Member who, String path, String json) throws Exception {
		return mvc.perform(withToken(post(path), who).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	// URL 문자열로 넘기면 MockMvc가 %를 다시 인코딩하므로 특수 문자는 파라미터로 보낸다.
	ResultActions searchAs(Member who, String query) throws Exception {
		return mvc.perform(withToken(get(CHATS).param("q", query), who));
	}

	private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, Member who) {
		return request.header("Authorization", "Bearer " + jwt.createAccessToken(who.getEmail(), "USER"));
	}

	long startChat(Member who, Member target) throws Exception {
		return json(postAs(who, CHATS, java.util.Map.of("targetSlug", target.getTag())).andExpect(status().isOk())).get("id").asLong();
	}

	long send(Member who, long roomId, String body) throws Exception {
		return json(postAs(who, CHATS + "/" + roomId, java.util.Map.of("body", body)).andExpect(status().isCreated())).get("id").asLong();
	}

	JsonNode json(ResultActions result) throws Exception {
		return mapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	/** 작업을 같은 시점에 시작해 결과를 모은다. */
	<T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> task : tasks) {
				futures.add(executor.submit(() -> {
					start.await();
					return task.call();
				}));
			}
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get());
			}
			return results;
		} finally {
			executor.shutdownNow();
		}
	}
}
