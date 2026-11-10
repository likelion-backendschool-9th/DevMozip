package com.devmozip.backend.websocket;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import com.devmozip.backend.BackendApplication;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.service.MessageService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 블루그린처럼 서버 두 개가 같은 Redis와 DB를 쓰는 상황을 실제 서버로 확인한다.
 * Redis를 멈췄다 다시 켜야 하므로 포트를 고정한다. 임의 포트는 재시작하면 바뀐다.
 */
@Testcontainers(disabledWithoutDocker = true)
class MessengerRedisSignalIntegrationTest {
	private static final int REDIS_PORT = freePort();
	private static final String BODY = "본문은-Redis를-거치지-않는다";
	// 응답은 Redis 타임아웃(2초)을 기다리지 않아야 한다.
	private static final long MAX_SEND_MILLIS = 1000;

	@Container
	@SuppressWarnings("deprecation")
	static final GenericContainer<?> REDIS = new FixedHostPortGenericContainer<>("redis:7-alpine")
		.withFixedExposedPort(REDIS_PORT, 6379);

	private static ConfigurableApplicationContext blue;
	private static ConfigurableApplicationContext green;
	private final List<TestSocket> opened = new ArrayList<>();

	private static int freePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static ConfigurableApplicationContext server() {
		return new SpringApplicationBuilder(BackendApplication.class)
			.profiles("test")
			.run(
				"--server.port=0",
				"--spring.datasource.url=jdbc:h2:mem:messenger-redis;DB_CLOSE_DELAY=-1",
				"--spring.datasource.driver-class-name=org.h2.Driver",
				"--spring.data.redis.host=localhost",
				"--spring.data.redis.port=" + REDIS_PORT,
				"--spring.data.redis.timeout=2s",
				"--messenger.realtime.redis.enabled=true",
				"--messenger.realtime.allowed-origin=" + TestSocket.ALLOWED_ORIGIN);
	}

	@BeforeAll static void startServers() {
		blue = server();
		green = server();
	}

	@AfterAll static void stopServers() {
		if (blue != null) {
			blue.close();
		}
		if (green != null) {
			green.close();
		}
	}

	@AfterEach void closeSockets() {
		opened.forEach(TestSocket::abort);
	}

	private static Member member() {
		return TestMembers.create(blue.getBean(MemberRepository.class));
	}

	private TestSocket connect(ConfigurableApplicationContext server, Member member) throws Exception {
		int port = server.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
		String token = server.getBean(JwtProvider.class).createAccessToken(member.getEmail(), "USER");
		TestSocket socket = TestSocket.connect(port, token);
		opened.add(socket);
		return socket;
	}

	private static long startChat(Member from, Member to) {
		return blue.getBean(MessageService.class).startDirectChat(from.getId(), to.getTag()).id();
	}

	private static long send(Member from, long room) {
		return blue.getBean(MessageService.class).sendDirectMessage(from.getId(), room, BODY).id();
	}

	private static String created(long room, long messageId) {
		return "{\"type\":\"message.created\",\"roomType\":\"direct\",\"roomId\":" + room + ",\"messageId\":" + messageId + "}";
	}

	private static void stopRedis() {
		REDIS.getDockerClient().stopContainerCmd(REDIS.getContainerId()).exec();
	}

	private static void startRedis() {
		REDIS.getDockerClient().startContainerCmd(REDIS.getContainerId()).exec();
	}

	private static void pauseRedis() {
		REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
	}

	private static void unpauseRedis() {
		REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
	}

	/** 조건이 맞을 때까지 메시지를 보내며 기다린다. 서버의 재연결 간격은 Lettuce가 정한다. */
	private static void awaitDelivery(String label, Member from, long room, TestSocket socket) throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
		while (System.nanoTime() < deadline) {
			long id;
			try {
				id = send(from, room);
			} catch (RuntimeException e) {
				throw new AssertionError(label + ": 전송이 실패하면 안 된다", e);
			}
			String expected = created(room, id);
			String frame;
			while ((frame = socket.frames.poll(1, TimeUnit.SECONDS)) != null) {
				if (expected.equals(frame)) {
					return;
				}
			}
		}
		fail(label + ": 60초 안에 전달되지 않았다");
	}

	private static long timed(Callable<Long> action, long[] slowest) throws Exception {
		long started = System.nanoTime();
		long result = action.call();
		slowest[0] = Math.max(slowest[0], Duration.ofNanos(System.nanoTime() - started).toMillis());
		return result;
	}

	@Test void runsWithRedis() {
		assertInstanceOf(RedisMessengerSignalPublisher.class, blue.getBean(MessengerSignalPublisher.class));
		assertInstanceOf(RedisMessengerSignalPublisher.class, green.getBean(MessengerSignalPublisher.class));
	}

	@Test void signalReachesMemberConnectedToTheOtherServer() throws Exception {
		Member alice = member();
		Member bob = member();
		long room = startChat(alice, bob);
		TestSocket bobOnGreen = connect(green, bob);
		TestSocket aliceOnBlue = connect(blue, alice);

		long id = send(alice, room);

		assertEquals(created(room, id), bobOnGreen.frames.poll(5, TimeUnit.SECONDS));
		assertEquals(created(room, id), aliceOnBlue.frames.poll(5, TimeUnit.SECONDS));
	}

	@Test void typingReachesPeerConnectedToTheOtherServer() throws Exception {
		Member alice = member();
		Member bob = member();
		long room = startChat(alice, bob);
		TestSocket aliceOnBlue = connect(blue, alice);
		TestSocket bobOnGreen = connect(green, bob);
		String typing = "{\"type\":\"typing\",\"roomType\":\"direct\",\"roomId\":" + room + "}";

		aliceOnBlue.sendText(typing);

		assertEquals(typing, bobOnGreen.frames.poll(5, TimeUnit.SECONDS));
		assertNull(aliceOnBlue.frames.poll(300, TimeUnit.MILLISECONDS));
	}

	@Test void memberOnEitherServerReceivesExactlyOnce() throws Exception {
		Member alice = member();
		Member bob = member();
		long room = startChat(alice, bob);
		TestSocket onBlue = connect(blue, bob);
		TestSocket onGreen = connect(green, bob);

		long id = send(alice, room);

		assertEquals(created(room, id), onBlue.frames.poll(5, TimeUnit.SECONDS));
		assertEquals(created(room, id), onGreen.frames.poll(5, TimeUnit.SECONDS));
		assertNull(onBlue.frames.poll(500, TimeUnit.MILLISECONDS));
		assertNull(onGreen.frames.poll(100, TimeUnit.MILLISECONDS));
	}

	@Test void memberOutsideTheRoomReceivesNothing() throws Exception {
		Member alice = member();
		Member bob = member();
		long room = startChat(alice, bob);
		TestSocket stranger = connect(green, member());
		TestSocket strangerOnBlue = connect(blue, member());
		TestSocket bobOnGreen = connect(green, bob);

		long id = send(alice, room);

		assertEquals(created(room, id), bobOnGreen.frames.poll(5, TimeUnit.SECONDS));
		assertNull(stranger.frames.poll(300, TimeUnit.MILLISECONDS));
		assertNull(strangerOnBlue.frames.poll(100, TimeUnit.MILLISECONDS));
	}

	@Test void channelCarriesRecipientsAndFrameButNotTheBody() throws Exception {
		Member alice = member();
		Member bob = member();
		long room = startChat(alice, bob);
		BlockingQueue<String> channel = new LinkedBlockingQueue<>();
		try (RedisConnection connection = blue.getBean(RedisConnectionFactory.class).getConnection()) {
			connection.subscribe((message, pattern) -> channel.add(new String(message.getBody(), StandardCharsets.UTF_8)),
				RedisMessengerSignalPublisher.CHANNEL.getBytes(StandardCharsets.UTF_8));
			Thread.sleep(300);

			long id = send(alice, room);

			String payload = channel.poll(5, TimeUnit.SECONDS);
			assertNotNull(payload);
			assertFalse(payload.contains(BODY), payload);
			RedisSignalPayload parsed = blue.getBean(tools.jackson.databind.ObjectMapper.class).readValue(payload, RedisSignalPayload.class);
			assertEquals(List.of(alice.getId(), bob.getId()), parsed.recipients());
			assertEquals(created(room, id), parsed.frame());
		}
	}

	// 서버 시작, 전송 응답, 같은 서버 전달, 복구를 한 흐름으로 확인한다.
	@Test void stoppedRedisKeepsSendingAndRecoversAfterRestart() throws Exception {
		Member alice = member();
		Member bob = member();
		long room = startChat(alice, bob);
		TestSocket aliceOnBlue = connect(blue, alice);
		TestSocket bobOnGreen = connect(green, bob);
		ConfigurableApplicationContext late = null;

		stopRedis();
		try {
			long[] slowest = {0};
			for (int i = 0; i < 5; i++) {
				long id = timed(() -> send(alice, room), slowest);
				assertEquals(created(room, id), aliceOnBlue.frames.poll(5, TimeUnit.SECONDS), "같은 서버로 직접 전달해야 한다");
			}
			assertTrue(slowest[0] < MAX_SEND_MILLIS, "가장 느린 전송 " + slowest[0] + "ms");
			assertNull(bobOnGreen.frames.poll(100, TimeUnit.MILLISECONDS), "다른 서버는 Redis 없이 받을 수 없다");

			// Redis가 멈춘 동안 시작한 서버도 떠야 한다. 지금까지는 Redis 없이도 떴다.
			late = server();
			assertTrue(late.isActive());
		} finally {
			startRedis();
		}
		try {
			awaitDelivery("Redis 재시작 후 다른 서버 구독 복구", alice, room, bobOnGreen);
			TestSocket bobOnLate = connect(late, bob);
			awaitDelivery("Redis가 멈춘 동안 시작한 서버의 구독", alice, room, bobOnLate);
		} finally {
			late.close();
		}
	}

	// 연결은 살아 있는데 응답이 없는 경우다. 명령마다 타임아웃까지 기다리게 된다.
	@Test void hangingRedisDoesNotDelaySending() throws Exception {
		Member alice = member();
		Member bob = member();
		long room = startChat(alice, bob);
		TestSocket aliceOnBlue = connect(blue, alice);
		TestSocket bobOnGreen = connect(green, bob);

		pauseRedis();
		try {
			long[] slowest = {0};
			for (int i = 0; i < 5; i++) {
				long id = timed(() -> send(alice, room), slowest);
				assertEquals(created(room, id), aliceOnBlue.frames.poll(5, TimeUnit.SECONDS), "같은 서버로 직접 전달해야 한다");
			}
			assertTrue(slowest[0] < MAX_SEND_MILLIS, "가장 느린 전송 " + slowest[0] + "ms");
		} finally {
			unpauseRedis();
		}
		awaitDelivery("Redis 재개 후 다른 서버 전달", alice, room, bobOnGreen);
	}
}
