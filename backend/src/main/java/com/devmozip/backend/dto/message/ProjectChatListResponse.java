package com.devmozip.backend.dto.message;

import java.util.List;

public record ProjectChatListResponse(List<ProjectChatListItemResponse> items, Long nextCursor) {
}
