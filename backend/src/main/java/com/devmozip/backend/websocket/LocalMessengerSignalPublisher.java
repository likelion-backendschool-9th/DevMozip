package com.devmozip.backend.websocket;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Redis 없이 실행하는 local과 테스트용. 이 서버에 연결된 회원에게만 전달한다. */
@Component
@ConditionalOnProperty(name = MessengerSignalPublisher.REDIS_ENABLED, havingValue = "false", matchIfMissing = true)
class LocalMessengerSignalPublisher implements MessengerSignalPublisher {
	private final MessengerSessionRegistry registry;

	LocalMessengerSignalPublisher(MessengerSessionRegistry registry) {
		this.registry = registry;
	}

	@Override
	public void publish(List<Long> recipients, String frame) {
		registry.send(recipients, frame);
	}
}
