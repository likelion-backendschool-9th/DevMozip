package com.devmozip.backend.utils;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

// 조건 API에만 적용한다. 소수·문자열을 정수로 자동 변환하지 않는다.
public class StrictIntegerDeserializer extends ValueDeserializer<Integer> {
	@Override
	public Integer deserialize(JsonParser parser, DeserializationContext context) {
		if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
			return context.reportInputMismatch(Integer.class, "정수를 입력해 주세요.");
		}
		return parser.getIntValue();
	}
}
