package com.devmozip.backend.dto.ai;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public record CreateRecommendationRequest(
	@tools.jackson.databind.annotation.JsonDeserialize(using = com.devmozip.backend.utils.StrictLongDeserializer.class) Long messageId) {
	public CreateRecommendationRequest {
		if (messageId != null && messageId <= 0) throw new IllegalArgumentException("메시지 ID를 확인해 주세요.");
	}
	@JsonAnySetter
	public void unknown(String key, Object value) {
		throw new IllegalArgumentException("알 수 없는 추천 요청 항목입니다: " + key);
	}
}
