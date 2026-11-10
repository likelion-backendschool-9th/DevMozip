package com.devmozip.backend.utils;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** 분석 호출은 JSON boolean true로만 명시한다. 문자열/숫자 자동 변환은 거절한다. */
public class StrictBooleanDeserializer extends ValueDeserializer<Boolean> {
	@Override
	public Boolean deserialize(JsonParser parser, DeserializationContext context) {
		if (parser.currentToken() == JsonToken.VALUE_TRUE) return true;
		if (parser.currentToken() == JsonToken.VALUE_FALSE) return false;
		throw new IllegalArgumentException("analyze는 JSON boolean이어야 합니다.");
	}
}
