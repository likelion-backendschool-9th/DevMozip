package com.devmozip.backend.dto.message;

import java.time.OffsetDateTime;
import com.devmozip.backend.config.MessengerConfiguration;
import com.devmozip.backend.entity.ChatMember;
import com.devmozip.backend.entity.Message;

public record ProjectChatMessageResponse(long id, String body, boolean mine, ChatSenderResponse sender, OffsetDateTime createdAt) {
	public static ProjectChatMessageResponse from(Message message, ChatMember sender, boolean mine) {
		return new ProjectChatMessageResponse(
			message.getId(),
			message.getContent(),
			mine,
			ChatSenderResponse.from(sender),
			message.getSentAt().atZone(MessengerConfiguration.ZONE).toOffsetDateTime());
	}
}
