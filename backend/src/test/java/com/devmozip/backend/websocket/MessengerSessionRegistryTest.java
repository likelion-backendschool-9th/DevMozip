package com.devmozip.backend.websocket;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.devmozip.backend.config.MessengerRealtimeProperties;
import com.devmozip.backend.security.jwt.SuspendedMemberStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 기본 시간 값(30초, 70초)과 만료 시각의 경계를 시계를 옮겨 가며 확인한다. */
class MessengerSessionRegistryTest {
	private static final Instant START = Instant.parse("2026-11-07T00:00:00Z");
	private static final Instant FAR_FUTURE = START.plus(Duration.ofDays(1));
	private static final String EMAIL = "member@devmozip.test";

	private final MovingClock clock = new MovingClock();
	private final SuspendedMemberStore suspended = mock(SuspendedMemberStore.class);
	private final MessengerRealtimeProperties properties = new MessengerRealtimeProperties();
	private final ExecutorService otherThread = Executors.newSingleThreadExecutor();
	private final CountDownLatch release = new CountDownLatch(1);
	private MessengerSessionRegistry registry;

	@BeforeEach void setUp() {
		// 주기 실행을 멈추고 시계를 옮긴 뒤 직접 확인을 실행한다.
		properties.setCheckInterval(Duration.ofHours(1));
		registry = new MessengerSessionRegistry(properties, suspended, clock);
		registry.start();
	}

	@AfterEach void tearDown() {
		release.countDown();
		otherThread.shutdownNow();
		registry.stop();
	}

	private WebSocketSession session(long memberId, Instant tokenExpiresAt) {
		WebSocketSession session = mock(WebSocketSession.class);
		Map<String, Object> attributes = new HashMap<>();
		attributes.put(MessengerHandshakeInterceptor.MEMBER_ID, memberId);
		attributes.put(MessengerHandshakeInterceptor.EMAIL, EMAIL);
		attributes.put(MessengerHandshakeInterceptor.TOKEN_EXPIRES_AT, tokenExpiresAt);
		when(session.getAttributes()).thenReturn(attributes);
		when(session.getId()).thenReturn("session-" + System.identityHashCode(session));
		return session;
	}

	private WebSocketSession connected(long memberId) {
		WebSocketSession session = session(memberId, FAR_FUTURE);
		registry.register(session);
		return session;
	}

	private void checkAt(Duration elapsed) {
		clock.now = START.plus(elapsed);
		registry.checkConnections();
	}

	private static TextMessage text(String payload) {
		return new TextMessage(payload);
	}

	private WebSocketSession blocked(long memberId) throws IOException {
		WebSocketSession session = connected(memberId);
		CountDownLatch sending = new CountDownLatch(1);
		doAnswer(invocation -> {
			sending.countDown();
			release.await();
			return null;
		}).when(session).sendMessage(any());
		otherThread.submit(() -> registry.send(memberId, "{\"type\":\"first\"}"));
		try {
			assertTrue(sending.await(5, TimeUnit.SECONDS));
		} catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}
		return session;
	}

	@Test void pingsEveryThirtySeconds() throws Exception {
		WebSocketSession session = connected(1);
		TextMessage ping = text("{\"type\":\"ping\"}");

		checkAt(Duration.ofSeconds(29));
		verify(session, never()).sendMessage(any());

		checkAt(Duration.ofSeconds(30));
		verify(session, times(1)).sendMessage(ping);
		registry.markPong(session);

		checkAt(Duration.ofSeconds(59));
		verify(session, times(1)).sendMessage(ping);

		checkAt(Duration.ofSeconds(60));
		verify(session, times(2)).sendMessage(ping);
	}

	@Test void closesWhenNoPongForSeventySeconds() throws Exception {
		WebSocketSession session = connected(1);

		checkAt(Duration.ofSeconds(69));
		verify(session, never()).close(any());

		checkAt(Duration.ofSeconds(70));
		verify(session).close(CloseStatus.SESSION_NOT_RELIABLE);
	}

	@Test void pongExtendsTheDeadline() throws Exception {
		WebSocketSession session = connected(1);

		clock.now = START.plus(Duration.ofSeconds(60));
		registry.markPong(session);

		checkAt(Duration.ofSeconds(129));
		verify(session, never()).close(any());
		checkAt(Duration.ofSeconds(130));
		verify(session).close(CloseStatus.SESSION_NOT_RELIABLE);
	}

	@Test void closesEachTabAtItsOwnTokenExpiry() throws Exception {
		WebSocketSession early = session(1, START.plus(Duration.ofSeconds(10)));
		WebSocketSession late = session(1, START.plus(Duration.ofSeconds(20)));
		registry.register(early);
		registry.register(late);

		checkAt(Duration.ofSeconds(9));
		verify(early, never()).close(any());

		checkAt(Duration.ofSeconds(10));
		verify(early).close(MessengerSessionRegistry.TOKEN_EXPIRED);
		verify(late, never()).close(any());

		checkAt(Duration.ofSeconds(20));
		verify(late).close(MessengerSessionRegistry.TOKEN_EXPIRED);
	}

	@Test void doesNotSendAfterTokenExpiry() throws Exception {
		WebSocketSession session = session(1, START.plus(Duration.ofSeconds(10)));
		registry.register(session);

		clock.now = START.plus(Duration.ofSeconds(10));
		registry.send(1, "{\"type\":\"signal\"}");

		verify(session, never()).sendMessage(any());
		verify(session).close(MessengerSessionRegistry.TOKEN_EXPIRED);
	}

	@Test void suspendedMemberReceivesNothingAndIsClosed() throws Exception {
		WebSocketSession session = connected(1);
		when(suspended.isSuspended(EMAIL)).thenReturn(true);

		registry.send(1, "{\"type\":\"signal\"}");

		verify(session, never()).sendMessage(any());
		verify(session).close(MessengerSessionRegistry.SUSPENDED);
	}

	@Test void withdrawnAccountReceivesNothingAndIsClosed() throws Exception {
		registry.stop();
		var accounts = mock(com.devmozip.backend.repository.MemberRepository.class);
		when(accounts.findByEmail(EMAIL)).thenReturn(java.util.Optional.empty());
		registry = new MessengerSessionRegistry(properties, suspended, clock, accounts);
		registry.start();
		WebSocketSession session = connected(1);
		registry.send(1, "{\"type\":\"signal\"}");
		verify(session, never()).sendMessage(any());
		verify(session).close(MessengerSessionRegistry.TOKEN_EXPIRED);
	}

	@Test void frameFromSuspendedMemberIsNotProcessed() throws Exception {
		WebSocketSession session = connected(1);
		assertFalse(registry.closeIfNotAllowed(session));

		when(suspended.isSuspended(EMAIL)).thenReturn(true);

		assertTrue(registry.closeIfNotAllowed(session));
		verify(session).close(MessengerSessionRegistry.SUSPENDED);
	}

	@Test void sendReachesOnlyTheTabsStillRegistered() throws Exception {
		WebSocketSession leaving = connected(1);
		WebSocketSession staying = connected(1);
		WebSocketSession other = connected(2);
		assertEquals(2, registry.connectionCount(1));

		registry.unregister(leaving);
		registry.send(1, "{\"type\":\"signal\"}");

		assertEquals(1, registry.connectionCount(1));
		verify(leaving, never()).sendMessage(any());
		verify(staying).sendMessage(text("{\"type\":\"signal\"}"));
		verify(other, never()).sendMessage(any());
	}

	@Test void lastTabLeavingForgetsTheMember() {
		WebSocketSession session = connected(1);
		registry.unregister(session);
		assertEquals(0, registry.connectionCount(1));
		assertDoesNotThrow(() -> registry.send(1, "{\"type\":\"signal\"}"));
	}

	@Test void concurrentSendDoesNotWaitForABlockedSend() throws Exception {
		properties.setSendTimeLimit(Duration.ofHours(1));
		WebSocketSession stuck = blocked(1);

		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> registry.send(1, "{\"type\":\"second\"}"));

		verify(stuck, times(1)).sendMessage(any());
		verify(stuck, never()).close(any());
	}

	@Test void connectionPastTheSendTimeLimitIsClosed() throws Exception {
		properties.setSendTimeLimit(Duration.ofMillis(50));
		WebSocketSession stuck = blocked(1);
		WebSocketSession otherTab = connected(1);
		Thread.sleep(100);

		registry.send(1, "{\"type\":\"second\"}");

		verify(stuck).close(CloseStatus.SESSION_NOT_RELIABLE);
		verify(otherTab).sendMessage(text("{\"type\":\"second\"}"));
	}

	@Test void connectionPastTheBufferLimitIsClosed() throws Exception {
		properties.setSendTimeLimit(Duration.ofHours(1));
		WebSocketSession stuck = blocked(1);

		registry.send(1, "x".repeat(64 * 1024 + 1));

		verify(stuck).close(CloseStatus.SESSION_NOT_RELIABLE);
	}

	@Test void failedSendClosesThatConnectionOnly() throws Exception {
		WebSocketSession broken = connected(1);
		WebSocketSession healthy = connected(1);
		doThrow(new IOException("broken pipe")).when(broken).sendMessage(any());

		registry.send(1, "{\"type\":\"signal\"}");

		verify(broken).close(CloseStatus.SESSION_NOT_RELIABLE);
		verify(healthy).sendMessage(text("{\"type\":\"signal\"}"));
	}

	@Test void unexpectedFailureDoesNotStopCheckingTheRest() throws Exception {
		WebSocketSession first = connected(1);
		WebSocketSession second = connected(2);
		WebSocketSession third = connected(3);
		for (WebSocketSession session : new WebSocketSession[] {first, second, third}) {
			doThrow(new IllegalArgumentException("unexpected")).when(session).sendMessage(any());
			doThrow(new IllegalArgumentException("unexpected")).when(session).close(any());
		}

		checkAt(Duration.ofSeconds(30));

		verify(first).sendMessage(any(WebSocketMessage.class));
		verify(second).sendMessage(any(WebSocketMessage.class));
		verify(third).sendMessage(any(WebSocketMessage.class));
	}

	@Test void stopClosesEveryConnectionWithGoingAway() throws Exception {
		WebSocketSession first = connected(1);
		WebSocketSession second = connected(1);
		WebSocketSession third = connected(2);

		registry.stop();

		assertFalse(registry.isRunning());
		verify(first).close(MessengerSessionRegistry.SHUTTING_DOWN);
		verify(second).close(MessengerSessionRegistry.SHUTTING_DOWN);
		verify(third).close(MessengerSessionRegistry.SHUTTING_DOWN);
		assertEquals(1001, MessengerSessionRegistry.SHUTTING_DOWN.getCode());
	}

	@Test void connectionArrivingDuringShutdownIsClosed() throws Exception {
		registry.stop();

		WebSocketSession late = connected(1);

		verify(late).close(MessengerSessionRegistry.SHUTTING_DOWN);
	}

	@Test void closeCodesMatchTheContract() {
		assertEquals(4401, MessengerSessionRegistry.TOKEN_EXPIRED.getCode());
		assertEquals(4403, MessengerSessionRegistry.SUSPENDED.getCode());
	}

	private static final class MovingClock extends Clock {
		private volatile Instant now = START;

		@Override public ZoneId getZone() {
			return ZoneId.of("Asia/Seoul");
		}

		@Override public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override public Instant instant() {
			return now;
		}
	}
}
