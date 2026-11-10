package com.devmozip.backend.service;

import com.devmozip.backend.dto.ai.UpdateConditionsRequest;
import com.devmozip.backend.dto.ai.DraftConditionsRequest;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** 기존 조건의 최상위 키는 유지하고 서버 관리 메타데이터만 분리한다. */
@Component
public class AiConditionsDocument {
	public static final List<String> FIELDS = List.of("interests", "stacks", "roles", "career", "durationMonths");
	private final ObjectMapper mapper;
	public AiConditionsDocument(ObjectMapper mapper) { this.mapper = mapper; }
	public ObjectNode read(String json) {
		var node = mapper.readTree(json == null ? "{}" : json);
		if (!node.isObject()) throw new IllegalArgumentException("조건 객체를 확인해 주세요.");
		return (ObjectNode) node;
	}
	public ObjectNode formal(ObjectNode document) {
		var copy = document.deepCopy();
		copy.remove("_analysis");
		copy.remove("_requirements");
		return copy;
	}
	public UpdateConditionsRequest recommendation(String json) {
		return mapper.treeToValue(formal(read(json)), UpdateConditionsRequest.class);
	}
	/** 선택 여부 안내다. 대화 수락을 막거나 누락값을 기본값으로 채우지 않는다. */
	public List<String> missingRequiredQuestions(String json) {
		var doc = read(json);
		var questions = new java.util.ArrayList<String>();
		if (!doc.path("interests").isArray() || doc.path("interests").isEmpty()) {
			questions.add("추천을 위해 관심 분야가 필요해요. 웹·모바일처럼 원하는 프로젝트 분야를 하나 이상 선택해 주실 수 있나요?");
		}
		if (!doc.hasNonNull("career") || doc.path("career").asText().isBlank()) {
			questions.add("함께할 분에게 원하는 최소 경력이 있나요? 경력 무관도 선택할 수 있어요.");
		}
		if (!doc.hasNonNull("durationMonths")) questions.add("프로젝트를 몇 개월 동안 진행하고 싶으신가요?");
		return List.copyOf(questions);
	}
	public String manual(String previous, UpdateConditionsRequest input) {
		return mergeManual(previous, mapper.valueToTree(input));
	}
	public String manualDraft(String previous, DraftConditionsRequest input) {
		return mergeManual(previous, mapper.valueToTree(input));
	}
	private String mergeManual(String previous, ObjectNode result) {
		var old = read(previous);
		if (old.has("_analysis")) {
			var metadata = old.withObject("_analysis").deepCopy();
			var sources = metadata.withObject("sources");
			for (String field : FIELDS) sources.set(field, mapper.valueToTree(java.util.Map.of("origin", "MANUAL")));
			metadata.remove("suggestions");
			metadata.remove("questions");
			result.set("_analysis", metadata);
		}
		return mapper.writeValueAsString(result);
	}
}
