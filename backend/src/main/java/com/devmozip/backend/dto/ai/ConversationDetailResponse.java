package com.devmozip.backend.dto.ai;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.LocalDateTime;
import java.util.List;

public record ConversationDetailResponse(long id, String title, @JsonRawValue String currentConditions,
	LocalDateTime createdAt, LocalDateTime updatedAt, List<MessageResponse> messages) {}
