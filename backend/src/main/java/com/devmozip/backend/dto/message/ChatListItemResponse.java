package com.devmozip.backend.dto.message;

public record ChatListItemResponse(long id, ChatPeerResponse peer, ChatMessageResponse lastMessage, long unreadCount) {
}
