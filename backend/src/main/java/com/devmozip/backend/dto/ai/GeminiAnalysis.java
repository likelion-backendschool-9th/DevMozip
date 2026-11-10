package com.devmozip.backend.dto.ai;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.List;

/** Gemini 제안이며 조건 적용 여부는 서버 병합 정책이 결정한다. */
public record GeminiAnalysis(String reply, List<String> questions, List<Change> changes, List<FactChange> facts) {
	public enum Origin { EXPLICIT, INFERRED }
	public enum Field { interests, stacks, roles, career, durationMonths }
	public enum Action { ADD, SET, REMOVE, CLEAR }
	public enum Kind { OWN_SKILL, LEARNING_SKILL, TARGET_SKILL, TARGET_EXPERIENCE, PURPOSE,
		USER_LEVEL, FEATURE, COLLABORATION_GOAL, LEARNING_GOAL, PROJECT_PREFERENCE, OTHER }
	public GeminiAnalysis {
		text(reply, 2000);
		questions = strings(questions, 8, 300);
		if (changes == null || changes.size() > 30 || changes.stream().anyMatch(java.util.Objects::isNull)
			|| facts == null || facts.size() > 50 || facts.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("분석 항목을 확인해 주세요.");
		changes = List.copyOf(changes);
		facts = List.copyOf(facts);
	}
	public record Change(Field field, Action action, List<String> values, Integer count, Origin origin,
		String evidence, Boolean explicitChange) {
		public Change {
			if (field == null || action == null || origin == null || explicitChange == null) throw new IllegalArgumentException("조건 변경 형식 오류");
			values = strings(values, 30, 50);
			text(evidence, 1500);
			if (count != null && (count < 1 || count > 20)) throw new IllegalArgumentException("인원 범위 오류");
			if (action == Action.CLEAR ? !values.isEmpty() : values.isEmpty()) throw new IllegalArgumentException("조건 변경 값 오류");
			if ((field == Field.roles || field == Field.career || field == Field.durationMonths)
				&& action != Action.CLEAR && values.size() != 1) throw new IllegalArgumentException("단일 조건 값 오류");
			if (field != Field.roles && count != null) throw new IllegalArgumentException("직군 외 인원 지정 오류");
			if (field == Field.roles && (action == Action.ADD || action == Action.SET) && count == null) {
				throw new IllegalArgumentException("명시되지 않은 인원은 질문해야 합니다.");
			}
		}
		@JsonAnySetter public void unknown(String key, Object value) { throw new IllegalArgumentException("조건 변경 필드 오류"); }
	}
	public record FactChange(Kind kind, Action action, String value, Origin origin, String evidence) {
		public FactChange {
			if (kind == null || action == null || origin == null) throw new IllegalArgumentException("요구사항 형식 오류");
			text(value, 500);
			text(evidence, 1500);
		}
		@JsonAnySetter public void unknown(String key, Object value) { throw new IllegalArgumentException("요구사항 필드 오류"); }
	}
	public static void text(String value, int limit) {
		if (value == null || value.isBlank() || value.length() > limit) throw new IllegalArgumentException("분석 문자열 형식 오류");
	}
	private static List<String> strings(List<String> values, int count, int length) {
		if (values == null || values.size() > count) throw new IllegalArgumentException("분석 배열 형식 오류");
		values.forEach(value -> text(value, length));
		return List.copyOf(values);
	}
	@JsonAnySetter public void unknown(String key, Object value) { throw new IllegalArgumentException("분석 응답 필드 오류"); }
}
