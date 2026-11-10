package com.devmozip.backend.dto.ai;

import com.devmozip.backend.utils.StrictBooleanDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateMessageRequest(String content,
	@JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean analyze, String clientKey) {}
