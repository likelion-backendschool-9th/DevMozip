package com.devmozip.backend.dto.ai;

import com.devmozip.backend.entity.AiMessage;
import java.time.LocalDateTime;

public record MessageResponse(long id, AiMessage.Role role, String content, LocalDateTime createdAt, String clientKey) {
	public static MessageResponse from(AiMessage message) {
		return new MessageResponse(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt(), message.getClientKey());
	}
}
