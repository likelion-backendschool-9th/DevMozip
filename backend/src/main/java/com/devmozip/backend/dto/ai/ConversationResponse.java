package com.devmozip.backend.dto.ai;

import com.devmozip.backend.entity.AiConversation;
import java.time.LocalDateTime;

public record ConversationResponse(long id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
	public static ConversationResponse from(AiConversation conversation) {
		return new ConversationResponse(conversation.getId(), conversation.getTitle(),
			conversation.getCreatedAt(), conversation.getUpdatedAt());
	}
}
