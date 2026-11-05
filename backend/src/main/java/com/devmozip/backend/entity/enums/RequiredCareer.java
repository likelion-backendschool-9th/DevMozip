package com.devmozip.backend.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

// 팀 경력 계약 확인 전 사용하는 AI 요구 경력 선택지다.
public enum RequiredCareer {
	ANY("경력 무관"), ONE_YEAR("1년 이상"), TWO_YEARS("2년 이상"),
	THREE_YEARS("3년 이상"), FIVE_YEARS("5년 이상");

	private final String label;

	RequiredCareer(String label) { this.label = label; }

	@JsonValue
	public String label() { return label; }

	@JsonCreator
	public static RequiredCareer fromLabel(String value) {
		for (RequiredCareer career : values()) {
			if (career.label.equals(value.strip())) return career;
		}
		throw new IllegalArgumentException("요구 경력을 확인해 주세요.");
	}
}
