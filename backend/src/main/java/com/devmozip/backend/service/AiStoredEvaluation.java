package com.devmozip.backend.service;

import com.devmozip.backend.dto.ai.internal.RecommendationResult.Score;
import tools.jackson.databind.ObjectMapper;

/** 기존 TEXT에 버전 있는 기본 평가 JSON을 저장한다. detailed_reason과 분리한다. */
public final class AiStoredEvaluation {
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private AiStoredEvaluation() {}
	public static String encode(Score score) {
		if (score.requirementEvaluation() == null) return String.join("\n", score.basicReasons());
		var value = MAPPER.valueToTree(score.requirementEvaluation());
		((tools.jackson.databind.node.ObjectNode) value).put("basicReason", String.join("\n", score.basicReasons()));
		return MAPPER.writeValueAsString(value);
	}
	public static com.devmozip.backend.dto.ai.RequirementEvaluation decode(String raw) {
		if (raw == null || !raw.startsWith("{")) return null;
		var node = MAPPER.readTree(raw);
		if (node.path("version").asInt() != 1 || !node.has("baseScore")) return null;
		return MAPPER.treeToValue(node, com.devmozip.backend.dto.ai.RequirementEvaluation.class);
	}
}
