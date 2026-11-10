package com.devmozip.backend.utils;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

// 추천 실행의 메시지 ID에서 소수·문자열 자동 변환을 차단한다.
public class StrictLongDeserializer extends ValueDeserializer<Long> {
	@Override
	public Long deserialize(JsonParser parser, DeserializationContext context) {
		if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
			return context.reportInputMismatch(Long.class, "정수 ID를 입력해 주세요.");
		}
		return parser.getLongValue();
	}
}
