package com.devmozip.backend.ai.dto;

import com.devmozip.backend.ai.entity.AiMessage;
import java.time.LocalDateTime;

public record MessageResponse(long id, AiMessage.Role role, String content, LocalDateTime createdAt) {
	public static MessageResponse from(AiMessage message) {
		return new MessageResponse(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
	}
}
