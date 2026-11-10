package com.devmozip.backend.dto.message;

/** 프로젝트 채팅 목록 항목. id는 프로젝트 ID다. */
public record ProjectChatListItemResponse(long id, String title, String status, long memberCount, long unreadCount) {
}
