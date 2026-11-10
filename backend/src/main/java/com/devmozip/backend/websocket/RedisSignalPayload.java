package com.devmozip.backend.websocket;

import java.util.List;

/** Redis 채널로 오가는 값. 받는 회원 ID와 WebSocket 프레임 문자열만 담는다. */
record RedisSignalPayload(List<Long> recipients, String frame) {
}
