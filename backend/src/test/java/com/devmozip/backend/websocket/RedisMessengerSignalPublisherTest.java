package com.devmozip.backend.websocket;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisMessengerSignalPublisherTest {
	private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
	private final RedisMessengerSignalPublisher publisher = new RedisMessengerSignalPublisher(redis, JsonMapper.builder().build());

	@Test void subscribedServerMeansDelivered() {
		when(redis.convertAndSend(anyString(), anyString())).thenReturn(2L);
		assertDoesNotThrow(() -> publisher.publish(List.of(1L), "{}"));
	}

	// 구독이 끊긴 동안에는 발행이 성공해도 아무도 받지 못한다. 실패로 처리해야 같은 서버 전달로 넘어간다.
	@Test void noSubscriberCountsAsFailure() {
		when(redis.convertAndSend(anyString(), anyString())).thenReturn(0L);
		assertThrows(IllegalStateException.class, () -> publisher.publish(List.of(1L), "{}"));
	}
}
