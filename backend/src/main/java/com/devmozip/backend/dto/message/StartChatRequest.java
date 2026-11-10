package com.devmozip.backend.dto.message;

import com.devmozip.backend.utils.StrictStringDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record StartChatRequest(@JsonDeserialize(using = StrictStringDeserializer.class) String targetSlug) {
}
