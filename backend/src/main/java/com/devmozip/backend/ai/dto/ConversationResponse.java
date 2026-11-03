package com.devmozip.backend.ai.dto;

import com.devmozip.backend.ai.entity.AiConversation;
import java.time.LocalDateTime;

public record ConversationResponse(long id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
	public static ConversationResponse from(AiConversation conversation) {
		return new ConversationResponse(conversation.getId(), conversation.getTitle(),
			conversation.getCreatedAt(), conversation.getUpdatedAt());
	}
}
