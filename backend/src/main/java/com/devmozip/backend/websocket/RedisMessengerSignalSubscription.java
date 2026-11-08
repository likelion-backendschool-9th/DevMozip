package com.devmozip.backend.websocket;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 신호 채널을 구독한다. 컨테이너를 그대로 시작하면 첫 구독이 실패할 때 예외가 나 Redis가 멈춘 동안 서버가 뜨지 못하므로,
 * 구독만 별도 스레드에서 성공할 때까지 다시 시도한다. 한 번 구독한 뒤의 재연결과 재구독은 Lettuce와 컨테이너가 맡는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = MessengerSignalPublisher.REDIS_ENABLED, havingValue = "true")
class RedisMessengerSignalSubscription implements SmartLifecycle, MessageListener, DisposableBean {
	private static final Duration RETRY_INTERVAL = Duration.ofSeconds(5);
	private static final int FAILURES_PER_LOG = 12;

	private final RedisMessageListenerContainer container = new RedisMessageListenerContainer();
	private final MessengerSignalDispatcher dispatcher;
	private final ObjectMapper mapper;
	private volatile boolean running;
	private Thread subscriber;

	RedisMessengerSignalSubscription(RedisConnectionFactory connectionFactory, MessengerSignalDispatcher dispatcher, ObjectMapper mapper) {
		this.dispatcher = dispatcher;
		this.mapper = mapper;
		container.setConnectionFactory(connectionFactory);
		// 받은 신호를 해석해 dispatcher로 넘기기만 하므로 스레드를 새로 만들지 않는다.
		container.setTaskExecutor(Runnable::run);
		container.addMessageListener(this, new ChannelTopic(RedisMessengerSignalPublisher.CHANNEL));
		container.afterPropertiesSet();
	}

	@Override
	public void onMessage(Message message, byte[] pattern) {
		try {
			RedisSignalPayload payload = mapper.readValue(message.getBody(), RedisSignalPayload.class);
			if (payload != null && payload.recipients() != null && payload.frame() != null) {
				dispatcher.deliverLater(payload.recipients(), payload.frame());
			}
		} catch (JacksonException e) {
			log.warn("메신저 신호의 형식이 올바르지 않아 무시합니다.");
		}
	}

	@Override
	public void start() {
		running = true;
		subscriber = new Thread(this::subscribe, "messenger-redis-subscribe");
		subscriber.setDaemon(true);
		subscriber.start();
	}

	private void subscribe() {
		int failures = 0;
		while (running) {
			try {
				container.start();
				return;
			} catch (RuntimeException e) {
				// 실패해도 시작된 상태로 남으므로 멈춰야 다시 시작할 수 있다.
				container.stop();
				if (failures++ % FAILURES_PER_LOG == 0) {
					log.warn("메신저 신호 채널을 구독하지 못했습니다. 다시 시도합니다: {}", e.getClass().getSimpleName());
				}
			}
			try {
				Thread.sleep(RETRY_INTERVAL);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	@Override
	public void stop() {
		running = false;
		if (subscriber != null) {
			subscriber.interrupt();
		}
		container.stop();
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public void destroy() throws Exception {
		container.destroy();
	}
}
