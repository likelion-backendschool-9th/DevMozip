package com.devmozip.backend.websocket;

import java.util.List;

/** 커밋 후 회원들에게 보낼 WebSocket 프레임. 메시지 본문을 넣지 않는다. */
public record MessengerSignal(List<Long> recipients, Object frame) {
}
