package com.devmozip.backend.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.devmozip.backend.config.JwtProperties;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.SuspendedMemberStore;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/** 실제 Tomcat에 실제 클라이언트로 연결한다. 시간 값은 기본값(30초, 70초)을 줄여 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
	"spring.datasource.url=jdbc:h2:mem:messenger-websocket",
	"spring.datasource.driver-class-name=org.h2.Driver",
	// DOMAINNAME 끝에 슬래시가 있어도 브라우저의 Origin과 일치해야 한다.
	"messenger.realtime.allowed-origin=http://localhost:3000/",
	"messenger.realtime.ping-interval=300ms",
	"messenger.realtime.pong-timeout=1500ms",
	"messenger.realtime.check-interval=50ms",
	"messenger.realtime.send-time-limit=300ms"
})
@ActiveProfiles("test")
class MessengerWebSocketTest {
	private static final String WS = "/ws/messages";
	private static final String ORIGIN = TestSocket.ALLOWED_ORIGIN;

	@LocalServerPort int port;
	@Autowired JwtProvider jwt;
	@Autowired JwtProperties jwtProperties;
	@Autowired MemberRepository members;
	@Autowired SuspendedMemberStore suspendedMembers;
	@Autowired MessengerSessionRegistry registry;
	@Autowired JdbcTemplate jdbc;
	private final List<TestSocket> opened = new ArrayList<>();

	@AfterEach void closeSockets() {
		opened.forEach(TestSocket::abort);
	}

	private Member member() {
		return TestMembers.create(members);
	}

	private String token(Member member) {
		return jwt.createAccessToken(member.getEmail(), "USER");
	}

	private String signedToken(String email, Instant expiresAt, String secret) {
		return Jwts.builder()
			.subject(email)
			.claim("role", "USER")
			.claim("type", "access")
			.issuedAt(new Date())
			.expiration(Date.from(expiresAt))
			.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
			.compact();
	}

	private TestSocket connect(Member member) throws Exception {
		return track(TestSocket.connect(port, token(member)));
	}

	private TestSocket track(TestSocket socket) {
		opened.add(socket);
		return socket;
	}

	private int handshake(String token, String origin) throws Exception {
		return TestSocket.handshakeStatus(port, WS, token, origin);
	}

	private void assertStillOpen(Member member, TestSocket socket) throws Exception {
		registry.send(member.getId(), "{\"type\":\"check\"}");
		assertEquals("{\"type\":\"check\"}", socket.frames.poll(3, TimeUnit.SECONDS));
		assertFalse(socket.isClosed());
	}

	@Test void validCookieFromAllowedOriginConnects() throws Exception {
		assertEquals(101, handshake(token(member()), ORIGIN));
	}

	@Test void missingCookieIsRejected() throws Exception {
		assertEquals(401, handshake(null, ORIGIN));
	}

	@Test void malformedTokenIsRejected() throws Exception {
		assertEquals(401, handshake("not-a-token", ORIGIN));
	}

	@Test void tokenSignedWithAnotherKeyIsRejected() throws Exception {
		String forged = signedToken(member().getEmail(), Instant.now().plus(1, ChronoUnit.HOURS), "another-secret-another-secret-another-secret");
		assertEquals(401, handshake(forged, ORIGIN));
	}

	@Test void expiredTokenIsRejected() throws Exception {
		String expired = signedToken(member().getEmail(), Instant.now().minus(1, ChronoUnit.MINUTES), jwtProperties.getSecret());
		assertEquals(401, handshake(expired, ORIGIN));
	}

	@Test void refreshTokenIsRejected() throws Exception {
		assertEquals(401, handshake(jwt.createRefreshToken(member().getEmail()), ORIGIN));
	}

	@Test void tokenOfUnknownMemberIsRejected() throws Exception {
		assertEquals(401, handshake(jwt.createAccessToken("nobody@devmozip.test", "USER"), ORIGIN));
	}

	@Test void memberInSuspendedStoreIsRejected() throws Exception {
		Member member = member();
		suspendedMembers.suspend(member.getEmail());
		assertEquals(403, handshake(token(member), ORIGIN));
	}

	// 정지 목록은 서버별 메모리다. 다른 색에서 정지됐어도 DB 상태로 막는다.
	@Test void memberSuspendedOnlyInDatabaseIsRejected() throws Exception {
		Member member = member();
		jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", member.getId());
		assertEquals(403, handshake(token(member), ORIGIN));
	}

	@ParameterizedTest
	@ValueSource(strings = {"https://evil.example", "http://localhost:3000.evil.example", "http://localhost:3001", "https://localhost:3000", "null"})
	void otherOriginIsRejectedEvenWithValidCookie(String origin) throws Exception {
		assertEquals(403, handshake(token(member()), origin));
	}

	@Test void missingOriginIsRejected() throws Exception {
		assertEquals(403, handshake(token(member()), null));
	}

	// 스프링 기본 검사는 Host와 같은 Origin을 허용 목록과 무관하게 통과시킨다.
	@Test void originSameAsServerButNotConfiguredIsRejected() throws Exception {
		assertEquals(403, handshake(token(member()), "http://localhost:" + port));
	}

	@Test void otherPathsStayBehindAuthentication() throws Exception {
		String token = token(member());
		assertEquals(401, TestSocket.handshakeStatus(port, "/ws/other", token, ORIGIN));
		assertEquals(401, TestSocket.handshakeStatus(port, "/ws/messages/other", token, ORIGIN));
		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpRequest post = HttpRequest.newBuilder(URI.create("http://localhost:" + port + WS))
				.POST(HttpRequest.BodyPublishers.noBody())
				.build();
			assertEquals(401, client.send(post, HttpResponse.BodyHandlers.discarding()).statusCode());
		}
	}

	@Test void answeringPingKeepsConnectionPastPongTimeout() throws Exception {
		Member member = member();
		TestSocket socket = connect(member);
		Thread.sleep(2200);
		assertTrue(socket.pings.get() >= 3, "핑 " + socket.pings.get() + "회");
		assertStillOpen(member, socket);
	}

	@Test void silentConnectionIsClosedAfterPongTimeout() throws Exception {
		Member member = member();
		TestSocket socket = track(TestSocket.connectSilent(port, token(member)));
		assertEquals(4500, socket.awaitClose());
		assertTrue(socket.pings.get() >= 2, "핑 " + socket.pings.get() + "회");
		assertEquals(0, registry.connectionCount(member.getId()));
	}

	@Test void everyTabOfTheMemberReceivesAndOthersDoNot() throws Exception {
		Member alice = member();
		Member bob = member();
		TestSocket first = connect(alice);
		TestSocket second = connect(alice);
		TestSocket third = connect(alice);
		TestSocket other = connect(bob);
		assertEquals(3, registry.connectionCount(alice.getId()));

		registry.send(alice.getId(), "{\"type\":\"signal\"}");

		assertEquals("{\"type\":\"signal\"}", first.frames.poll(3, TimeUnit.SECONDS));
		assertEquals("{\"type\":\"signal\"}", second.frames.poll(3, TimeUnit.SECONDS));
		assertEquals("{\"type\":\"signal\"}", third.frames.poll(3, TimeUnit.SECONDS));
		assertNull(other.frames.poll(300, TimeUnit.MILLISECONDS));
	}

	@Test void closingOneTabKeepsTheOthers() throws Exception {
		Member member = member();
		TestSocket closing = connect(member);
		TestSocket staying = connect(member);

		closing.sendText("{\"type\":\"unknown\"}");
		assertEquals(1008, closing.awaitClose());

		assertEquals(1, registry.connectionCount(member.getId()));
		assertStillOpen(member, staying);
	}

	@ParameterizedTest
	@ValueSource(strings = {"not json", "{", "", "[]", "null", "1", "{}", "{\"type\":null}", "{\"type\":[\"pong\"]}", "{\"type\":\"unknown\"}",
		"{\"type\":\"typing\"}", "{\"type\":\"typing\",\"roomType\":\"direct\"}", "{\"type\":\"typing\",\"roomId\":1}",
		"{\"type\":\"typing\",\"roomType\":\"group\",\"roomId\":1}", "{\"type\":\"typing\",\"roomType\":\"direct\",\"roomId\":0}",
		"{\"type\":\"typing\",\"roomType\":\"direct\",\"roomId\":-1}", "{\"type\":\"typing\",\"roomType\":\"direct\",\"roomId\":\"1\"}",
		"{\"type\":\"typing\",\"roomType\":\"direct\",\"roomId\":1.5}", "{\"type\":\"typing\",\"roomType\":\"direct\",\"roomId\":99999999999999999999}"})
	void frameOtherThanPongOrValidTypingIsClosedWith1008(String frame) throws Exception {
		TestSocket socket = connect(member());
		socket.sendText(frame);
		assertEquals(1008, socket.awaitClose());
	}

	@Test void binaryFrameIsClosedWith1008() throws Exception {
		TestSocket socket = connect(member());
		socket.sendBinary(new byte[] {1, 2, 3});
		assertEquals(1008, socket.awaitClose());
	}

	@Test void frameOverLimitIsClosedWith1009() throws Exception {
		TestSocket socket = connect(member());
		socket.sendText(paddedPong(MessengerWebSocketHandler.MAX_FRAME_SIZE + 1));
		assertEquals(1009, socket.awaitClose());
	}

	@Test void frameAtLimitIsAccepted() throws Exception {
		Member member = member();
		TestSocket socket = connect(member);
		socket.sendText(paddedPong(MessengerWebSocketHandler.MAX_FRAME_SIZE));
		assertStillOpen(member, socket);
	}

	private static String paddedPong(int length) {
		String head = "{\"type\":\"pong\",\"padding\":\"";
		String tail = "\"}";
		String frame = head + "x".repeat(length - head.length() - tail.length()) + tail;
		assertEquals(length, frame.length());
		return frame;
	}

	// JWT의 만료 시각은 초 단위라 올림한 값을 쓴다.
	@Test void connectionIsClosedWith4401WhenTokenExpires() throws Exception {
		Member member = member();
		Instant expiresAt = Instant.now().plusSeconds(2).truncatedTo(ChronoUnit.SECONDS);
		TestSocket socket = track(TestSocket.connect(port, signedToken(member.getEmail(), expiresAt, jwtProperties.getSecret())));

		assertEquals(4401, socket.awaitClose());

		assertFalse(socket.closedAt.isBefore(expiresAt), socket.closedAt + " < " + expiresAt);
		assertTrue(socket.closedAt.isBefore(expiresAt.plusSeconds(1)), socket.closedAt + " >= " + expiresAt.plusSeconds(1));
		assertEquals(0, registry.connectionCount(member.getId()));
	}

	@Test void connectionIsClosedWith4403WhenMemberIsSuspended() throws Exception {
		Member member = member();
		TestSocket first = connect(member);
		TestSocket second = connect(member);

		suspendedMembers.suspend(member.getEmail());

		assertEquals(4403, first.awaitClose());
		assertEquals(4403, second.awaitClose());
		assertEquals(0, registry.connectionCount(member.getId()));
		assertEquals(403, handshake(token(member), ORIGIN));
	}

	// 수신을 멈춘 클라이언트로 소켓 버퍼를 채운다. Tomcat 기본값이면 전송 한 번이 20초 동안 막힌다.
	@Test void connectionThatStoppedReadingIsClosedWithoutHoldingOthers() throws Exception {
		Member stuck = member();
		Member other = member();
		track(TestSocket.connectNotReading(port, token(stuck)));
		TestSocket healthy = connect(other);
		String large = "x".repeat(1024 * 1024);

		long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
		long slowest = 0;
		while (registry.connectionCount(stuck.getId()) > 0 && System.nanoTime() < deadline) {
			long started = System.nanoTime();
			registry.send(stuck.getId(), large);
			slowest = Math.max(slowest, System.nanoTime() - started);
		}

		assertEquals(0, registry.connectionCount(stuck.getId()));
		assertTrue(slowest < Duration.ofSeconds(5).toNanos(), "가장 느린 전송 " + Duration.ofNanos(slowest));
		assertStillOpen(other, healthy);
	}
}
