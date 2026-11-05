package com.devmozip.backend.utils;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

// 자유 입력도 문자열만 받으며 숫자·boolean을 기술명/분야명으로 자동 변환하지 않는다.
public class StrictStringDeserializer extends ValueDeserializer<String> {
	@Override
	public String deserialize(JsonParser parser, DeserializationContext context) {
		if (parser.currentToken() != JsonToken.VALUE_STRING) {
			return context.reportInputMismatch(String.class, "문자열을 입력해 주세요.");
		}
		return parser.getString();
	}
}
