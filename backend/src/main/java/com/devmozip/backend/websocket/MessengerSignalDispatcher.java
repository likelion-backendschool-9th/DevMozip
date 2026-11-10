package com.devmozip.backend.websocket;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * 커밋된 뒤에만 신호를 내보낸다. 동기로 발행하면 Redis가 멈췄을 때 전송 응답이 타임아웃(2초)만큼 늦어지므로 전용 스레드에서 처리한다.
 * 스레드 풀을 빈으로 등록하면 스프링 부트의 applicationTaskExecutor 자동 구성이 빠지므로 여기서 직접 만든다.
 */
@Slf4j
@Component
public class MessengerSignalDispatcher {
	static final int QUEUE_CAPACITY = 1000;
	static final Duration PUBLISH_RETRY_INTERVAL = Duration.ofSeconds(5);
	static final Duration FAILURE_LOG_INTERVAL = Duration.ofMinutes(1);

	private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
		new ArrayBlockingQueue<>(QUEUE_CAPACITY), task -> {
			Thread thread = new Thread(task, "messenger-signal");
			thread.setDaemon(true);
			return thread;
		});
	private final MessengerSignalPublisher publisher;
	private final MessengerSessionRegistry registry;
	private final ObjectMapper mapper;
	private final Clock clock;
	// 작업 스레드에서만 쓴다.
	private Instant skipRedisUntil;
	private volatile Instant lastFailureLog;

	public MessengerSignalDispatcher(MessengerSignalPublisher publisher, MessengerSessionRegistry registry,
		ObjectMapper mapper, @Qualifier("messengerClock") Clock clock) {
		this.publisher = publisher;
		this.registry = registry;
		this.mapper = mapper;
		this.clock = clock;
	}

	@TransactionalEventListener
	public void onSignal(MessengerSignal signal) {
		dispatch(signal.recipients(), signal.frame());
	}

	/** 트랜잭션 없이 바로 보낼 신호(입력 중 표시)에 쓴다. 저장한 내용이 없으므로 커밋을 기다리지 않는다. */
	public void dispatch(List<Long> recipients, Object signal) {
		String frame = mapper.writeValueAsString(signal);
		try {
			worker.execute(() -> publish(recipients, frame));
		} catch (RejectedExecutionException e) {
			registry.send(recipients, frame);
		}
	}

	/** Redis에서 받은 신호를 전달한다. 호출하는 스레드가 Redis 연결의 스레드이므로 막히면 안 된다. */
	void deliverLater(List<Long> recipients, String frame) {
		try {
			worker.execute(() -> registry.send(recipients, frame));
		} catch (RejectedExecutionException e) {
			// 유실돼도 폴링과 after 조회가 보완한다.
			warnOncePerMinute("메신저 신호 대기열이 가득 차 받은 신호를 버립니다.");
		}
	}

	private void publish(List<Long> recipients, String frame) {
		// 장애 중에 신호마다 타임아웃을 기다리면 대기열이 밀려 같은 서버의 전달까지 늦어지므로 잠시 Redis를 건너뛴다.
		if (skipRedisUntil != null && clock.instant().isBefore(skipRedisUntil)) {
			registry.send(recipients, frame);
			return;
		}
		try {
			publisher.publish(recipients, frame);
			skipRedisUntil = null;
		} catch (RuntimeException e) {
			skipRedisUntil = clock.instant().plus(PUBLISH_RETRY_INTERVAL);
			warnOncePerMinute("메신저 신호를 발행하지 못해 같은 서버로 직접 전달합니다: " + e.getClass().getSimpleName());
			registry.send(recipients, frame);
		}
	}

	private void warnOncePerMinute(String message) {
		Instant now = clock.instant();
		if (lastFailureLog == null || !now.isBefore(lastFailureLog.plus(FAILURE_LOG_INTERVAL))) {
			lastFailureLog = now;
			log.warn(message);
		}
	}

	@PreDestroy
	void shutdown() {
		worker.shutdownNow();
	}
}
