package com.devmozip.backend.dto.message;

import java.util.List;

public record ChatListResponse(List<ChatListItemResponse> items, Long nextCursor) {
}
