package com.devmozip.backend.websocket;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 블루그린의 두 백엔드가 모두 구독하는 채널로 보낸다. 받은 서버가 자기에게 연결된 회원에게만 전달한다. */
@Component
@ConditionalOnProperty(name = MessengerSignalPublisher.REDIS_ENABLED, havingValue = "true")
class RedisMessengerSignalPublisher implements MessengerSignalPublisher {
	static final String CHANNEL = "messenger:signals";

	private final StringRedisTemplate redis;
	private final ObjectMapper mapper;

	RedisMessengerSignalPublisher(StringRedisTemplate redis, ObjectMapper mapper) {
		this.redis = redis;
		this.mapper = mapper;
	}

	@Override
	public void publish(List<Long> recipients, String frame) {
		Long receivers = redis.convertAndSend(CHANNEL, mapper.writeValueAsString(new RedisSignalPayload(recipients, frame)));
		// Redis가 재시작한 직후처럼 구독이 끊겨 있으면 아무도 받지 못하므로 실패로 처리해 직접 전달한다.
		if (receivers == null || receivers == 0) {
			throw new IllegalStateException("구독 중인 서버가 없습니다.");
		}
	}
}
