package com.devmozip.backend.dto.message;

import com.devmozip.backend.utils.StrictStringDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SendMessageRequest(@JsonDeserialize(using = StrictStringDeserializer.class) String body) {
}
