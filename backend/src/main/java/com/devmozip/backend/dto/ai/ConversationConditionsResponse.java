package com.devmozip.backend.dto.ai;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.LocalDateTime;

public record ConversationConditionsResponse(long id, @JsonRawValue String currentConditions, LocalDateTime updatedAt) {}
