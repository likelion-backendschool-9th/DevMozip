package com.devmozip.backend.config;

import com.devmozip.backend.websocket.MessengerHandshakeInterceptor;
import com.devmozip.backend.websocket.MessengerWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
public class MessengerWebSocketConfig implements WebSocketConfigurer {
	private final MessengerWebSocketHandler handler;
	private final MessengerHandshakeInterceptor interceptor;
	private final MessengerRealtimeProperties properties;

	public MessengerWebSocketConfig(MessengerWebSocketHandler handler, MessengerHandshakeInterceptor interceptor,
		MessengerRealtimeProperties properties) {
		this.handler = handler;
		this.interceptor = interceptor;
		this.properties = properties;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		WebSocketHandlerRegistration registration = registry.addHandler(handler, "/ws/messages").addInterceptors(interceptor);
		// 스프링의 기본 Origin 검사가 뒤에 항상 붙으므로 같은 값을 알려 준다. 실제 판단은 interceptor가 먼저 한다.
		String origin = properties.normalizedOrigin();
		if (origin != null) {
			registration.setAllowedOrigins(origin);
		}
	}
}
