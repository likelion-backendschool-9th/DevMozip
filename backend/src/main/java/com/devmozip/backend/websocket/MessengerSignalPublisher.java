package com.devmozip.backend.websocket;

import java.util.List;

/** 신호를 내보내는 방식. messenger.realtime.redis.enabled로 Redis와 같은 서버 전달 중에서 고른다. */
public interface MessengerSignalPublisher {
	String REDIS_ENABLED = "messenger.realtime.redis.enabled";

	/** 실패하면 예외를 던진다. 호출한 쪽이 같은 서버의 연결로 직접 전달한다. */
	void publish(List<Long> recipients, String frame);
}
