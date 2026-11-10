package com.devmozip.backend.dto.message;

import java.time.OffsetDateTime;
import com.devmozip.backend.config.MessengerConfiguration;
import com.devmozip.backend.entity.Message;

public record ChatMessageResponse(long id, String body, boolean mine, OffsetDateTime createdAt) {
	// 화면이 new Date()로 읽으므로 오프셋이 없으면 브라우저 시간대로 해석된다.
	public static ChatMessageResponse from(Message message, boolean mine) {
		return new ChatMessageResponse(
			message.getId(),
			message.getContent(),
			mine,
			message.getSentAt().atZone(MessengerConfiguration.ZONE).toOffsetDateTime());
	}
}
