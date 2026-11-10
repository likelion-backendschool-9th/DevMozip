package com.devmozip.backend.websocket;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.devmozip.backend.dto.message.MessageCreatedFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// 전용 스레드를 쓰지 않게 되면 이 테스트들이 호출 스레드에서 멈추므로, 멈추는 대신 실패하게 한다.
@Timeout(30)
class MessengerSignalDispatcherTest {
	private static final List<Long> DRAIN_RECIPIENTS = List.of(100L);
	private static final String DRAIN = "drain";
	private static final List<Long> RECIPIENTS = List.of(1L, 2L);
	private static final MessengerSignal SIGNAL = new MessengerSignal(RECIPIENTS, MessageCreatedFrame.direct(12, 341));
	private static final String FRAME = "{\"type\":\"message.created\",\"roomType\":\"direct\",\"roomId\":12,\"messageId\":341}";
	private static final long WAIT = 5000;

	private final MovingClock clock = new MovingClock();
	private final MessengerSignalPublisher publisher = mock(MessengerSignalPublisher.class);
	private final MessengerSessionRegistry registry = mock(MessengerSessionRegistry.class);
	private final CountDownLatch release = new CountDownLatch(1);
	private final Semaphore drained = new Semaphore(0);
	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
	private final Logger logger = (Logger) LoggerFactory.getLogger(MessengerSignalDispatcher.class);
	private MessengerSignalDispatcher dispatcher;

	@BeforeEach void setUp() {
		// Mockito의 스터빙은 스레드에 안전하지 않다. 작업 스레드가 돌기 전에 끝내야 한다.
		doAnswer(invocation -> {
			drained.release();
			return null;
		}).when(registry).send(DRAIN_RECIPIENTS, DRAIN);
		dispatcher = new MessengerSignalDispatcher(publisher, registry, JsonMapper.builder().build(), clock);
		logs.start();
		logger.addAppender(logs);
	}

	@AfterEach void tearDown() {
		release.countDown();
		dispatcher.shutdown();
		logger.detachAppender(logs);
	}

	private void failPublishing() {
		doThrow(new IllegalStateException("redis down")).when(publisher).publish(anyList(), anyString());
	}

	/** 작업 스레드가 전부 처리할 때까지 기다린다. */
	private void drain() throws Exception {
		dispatcher.deliverLater(DRAIN_RECIPIENTS, DRAIN);
		assertTrue(drained.tryAcquire(WAIT, TimeUnit.MILLISECONDS));
	}

	@Test void publishesTheFrameAsJson() {
		dispatcher.onSignal(SIGNAL);

		verify(publisher, timeout(WAIT)).publish(RECIPIENTS, FRAME);
		verify(registry, never()).send(anyList(), anyString());
	}

	@Test void publishingNeverRunsOnTheCallingThread() throws Exception {
		AtomicReference<Thread> publishedOn = new AtomicReference<>();
		CountDownLatch published = new CountDownLatch(1);
		doAnswer(invocation -> {
			publishedOn.set(Thread.currentThread());
			published.countDown();
			release.await();
			return null;
		}).when(publisher).publish(anyList(), anyString());

		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> dispatcher.onSignal(SIGNAL));

		assertTrue(published.await(WAIT, TimeUnit.MILLISECONDS));
		assertNotEquals(Thread.currentThread(), publishedOn.get());
		assertEquals("messenger-signal", publishedOn.get().getName());
	}

	@Test void failedPublishDeliversToThisServer() throws Exception {
		failPublishing();

		dispatcher.onSignal(SIGNAL);

		verify(registry, timeout(WAIT)).send(RECIPIENTS, FRAME);
	}

	@Test void skipsRedisForAWhileAfterFailureThenTriesAgain() throws Exception {
		failPublishing();
		dispatcher.onSignal(SIGNAL);
		verify(registry, timeout(WAIT).times(1)).send(RECIPIENTS, FRAME);

		clock.advance(MessengerSignalDispatcher.PUBLISH_RETRY_INTERVAL.minusMillis(1));
		dispatcher.onSignal(SIGNAL);
		dispatcher.onSignal(SIGNAL);
		verify(registry, timeout(WAIT).times(3)).send(RECIPIENTS, FRAME);
		verify(publisher, times(1)).publish(anyList(), anyString());

		clock.advance(Duration.ofMillis(1));
		doNothing().when(publisher).publish(anyList(), anyString());
		dispatcher.onSignal(SIGNAL);
		verify(publisher, timeout(WAIT).times(2)).publish(anyList(), anyString());
		drain();
		verify(registry, times(3)).send(RECIPIENTS, FRAME);
	}

	@Test void successAfterFailurePublishesEveryLaterSignal() throws Exception {
		failPublishing();
		dispatcher.onSignal(SIGNAL);
		verify(registry, timeout(WAIT)).send(RECIPIENTS, FRAME);

		clock.advance(MessengerSignalDispatcher.PUBLISH_RETRY_INTERVAL);
		doNothing().when(publisher).publish(anyList(), anyString());
		dispatcher.onSignal(SIGNAL);
		dispatcher.onSignal(SIGNAL);

		verify(publisher, timeout(WAIT).times(3)).publish(anyList(), anyString());
		verify(registry, times(1)).send(RECIPIENTS, FRAME);
	}

	@Test void fullQueueDeliversDirectlyOnTheCallingThread() throws Exception {
		CountDownLatch published = new CountDownLatch(1);
		doAnswer(invocation -> {
			published.countDown();
			release.await();
			return null;
		}).when(publisher).publish(anyList(), anyString());
		dispatcher.onSignal(SIGNAL);
		assertTrue(published.await(WAIT, TimeUnit.MILLISECONDS));
		for (int i = 0; i < MessengerSignalDispatcher.QUEUE_CAPACITY; i++) {
			dispatcher.onSignal(SIGNAL);
		}
		verify(registry, never()).send(anyList(), anyString());
		AtomicReference<Thread> deliveredOn = new AtomicReference<>();
		doAnswer(invocation -> {
			deliveredOn.set(Thread.currentThread());
			return null;
		}).when(registry).send(RECIPIENTS, FRAME);

		dispatcher.onSignal(SIGNAL);

		assertEquals(Thread.currentThread(), deliveredOn.get());
	}

	@Test void signalFromRedisRunsOnTheWorkerSoTheRedisThreadIsNotHeld() throws Exception {
		AtomicReference<Thread> deliveredOn = new AtomicReference<>();
		CountDownLatch delivered = new CountDownLatch(1);
		doAnswer(invocation -> {
			deliveredOn.set(Thread.currentThread());
			delivered.countDown();
			release.await();
			return null;
		}).when(registry).send(RECIPIENTS, FRAME);

		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> dispatcher.deliverLater(RECIPIENTS, FRAME));

		assertTrue(delivered.await(WAIT, TimeUnit.MILLISECONDS));
		assertEquals("messenger-signal", deliveredOn.get().getName());
	}

	@Test void signalFromRedisIsDroppedWithoutBlockingWhenTheQueueIsFull() throws Exception {
		CountDownLatch delivered = new CountDownLatch(1);
		AtomicInteger deliveries = new AtomicInteger();
		doAnswer(invocation -> {
			deliveries.incrementAndGet();
			delivered.countDown();
			release.await();
			return null;
		}).when(registry).send(RECIPIENTS, FRAME);
		dispatcher.deliverLater(RECIPIENTS, FRAME);
		assertTrue(delivered.await(WAIT, TimeUnit.MILLISECONDS));
		for (int i = 0; i < MessengerSignalDispatcher.QUEUE_CAPACITY; i++) {
			dispatcher.deliverLater(RECIPIENTS, FRAME);
		}

		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
			dispatcher.deliverLater(RECIPIENTS, FRAME);
			dispatcher.deliverLater(RECIPIENTS, FRAME);
		});

		assertEquals(1, deliveries.get());
		assertEquals(1, warnings().size());
	}

	@Test void failureIsLoggedOncePerMinuteWithoutTheSignal() throws Exception {
		failPublishing();

		for (int round = 0; round < 3; round++) {
			clock.advance(MessengerSignalDispatcher.PUBLISH_RETRY_INTERVAL);
			dispatcher.onSignal(SIGNAL);
			drain();
		}
		assertEquals(1, warnings().size());

		clock.advance(MessengerSignalDispatcher.FAILURE_LOG_INTERVAL);
		dispatcher.onSignal(SIGNAL);
		drain();
		assertEquals(2, warnings().size());

		for (String warning : warnings()) {
			assertFalse(warning.contains("341"), warning);
			assertFalse(warning.contains("message.created"), warning);
		}
	}

	private List<String> warnings() {
		return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
	}

	private static final class MovingClock extends Clock {
		private volatile Instant now = Instant.parse("2026-11-07T00:00:00Z");

		void advance(Duration amount) {
			now = now.plus(amount);
		}

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
