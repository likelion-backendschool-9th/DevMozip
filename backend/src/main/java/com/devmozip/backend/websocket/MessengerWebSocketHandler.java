package com.devmozip.backend.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 클라이언트 프레임을 처리한다. 메시지 본문은 REST로 오가고 여기서는 연결 확인 응답과 입력 중 신호만 받는다. */
@Slf4j
@Component
public class MessengerWebSocketHandler extends TextWebSocketHandler {
	static final int MAX_FRAME_SIZE = 4096;
	private static final String PONG = "pong";
	private static final String TYPING = "typing";

	private final MessengerSessionRegistry registry;
	private final MessengerTypingRelay typing;
	private final ObjectMapper mapper;

	public MessengerWebSocketHandler(MessengerSessionRegistry registry, MessengerTypingRelay typing, ObjectMapper mapper) {
		this.registry = registry;
		this.typing = typing;
		this.mapper = mapper;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		// 서버 전체 제한(ServletServerContainerFactoryBean)은 실제 서버가 없는 MockMvc 컨텍스트에서 시작에 실패하므로 연결마다 정한다.
		session.setTextMessageSizeLimit(MAX_FRAME_SIZE);
		session.setBinaryMessageSizeLimit(MAX_FRAME_SIZE);
		registry.register(session);
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		if (registry.closeIfNotAllowed(session)) {
			return;
		}
		JsonNode frame = parse(message);
		String type = text(frame, "type");
		if (PONG.equals(type)) {
			registry.markPong(session);
		} else if (TYPING.equals(type) && validTyping(frame)) {
			relayTyping(session, frame);
		} else {
			registry.close(session, CloseStatus.POLICY_VIOLATION);
		}
	}

	@Override
	protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
		registry.close(session, CloseStatus.POLICY_VIOLATION);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		registry.unregister(session);
	}

	// 입력 중 표시는 유실을 허용한다. 조회가 실패해도 연결은 유지한다.
	private void relayTyping(WebSocketSession session, JsonNode frame) {
		Long memberId = (Long) session.getAttributes().get(MessengerHandshakeInterceptor.MEMBER_ID);
		try {
			typing.relay(session, memberId, text(frame, "roomType"), frame.get("roomId").longValue());
		} catch (RuntimeException e) {
			log.debug("입력 중 신호를 전달하지 못했습니다: {}", e.getClass().getSimpleName());
		}
	}

	private static boolean validTyping(JsonNode frame) {
		JsonNode roomId = frame.get("roomId");
		return MessengerTypingRelay.supportsRoomType(text(frame, "roomType"))
			&& roomId != null && roomId.isIntegralNumber() && roomId.canConvertToLong() && roomId.longValue() > 0;
	}

	private JsonNode parse(TextMessage message) {
		try {
			JsonNode frame = mapper.readTree(message.getPayload());
			return frame != null && frame.isObject() ? frame : null;
		} catch (JacksonException e) {
			return null;
		}
	}

	private static String text(JsonNode frame, String field) {
		JsonNode value = frame == null ? null : frame.get(field);
		return value != null && value.isString() ? value.stringValue() : null;
	}
}
