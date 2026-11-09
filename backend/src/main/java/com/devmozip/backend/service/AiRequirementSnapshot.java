package com.devmozip.backend.service;

import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** 서버가 보존한 사실만 고정한다. 사용자 자신의 기술·추론은 평가 대상과 분리한다. */
@Component
public class AiRequirementSnapshot {
	public record Requirement(String id, String kind, String value, String evidence, long messageId, String mode) {
		public Requirement(String id, String kind, String value, String evidence, long messageId) {
			this(id, kind, value, evidence, messageId, mode(value + " " + evidence));
		}
		private static String mode(String text) {
			if (text.matches("(?is).*(필수.{0,8}(아니|없|않)|not required|optional).*")) return "PREFERENCE";
			return text.matches("(?is).*(필수|반드시|꼭|must|required).*") ? "MANDATORY_UNENFORCED" : "PREFERENCE";
		}
	}
	private static final Set<String> TARGET = Set.of("TARGET_SKILL", "TARGET_EXPERIENCE", "FEATURE",
		"PROJECT_PREFERENCE", "COLLABORATION_GOAL", "LEARNING_GOAL");
	private final ObjectMapper mapper;
	private final AiConditionsDocument documents;
	public AiRequirementSnapshot(ObjectMapper mapper, AiConditionsDocument documents) { this.mapper = mapper; this.documents = documents; }
	public String capture(String current, String formal, String text) {
		ObjectNode snapshot = documents.read(formal);
		var analysis = documents.read(current).path("_analysis");
		var meta = mapper.createObjectNode();
		meta.put("version", 1);
		meta.put("policy", "requirements-80-20-v1");
		meta.set("facts", analysis.path("facts").isArray() ? analysis.path("facts").deepCopy() : mapper.createArrayNode());
		meta.set("sources", analysis.path("sources").isObject() ? analysis.path("sources").deepCopy() : mapper.createObjectNode());
		if (text != null) meta.put("comparisonText", text);
		snapshot.set("_requirements", meta);
		return mapper.writeValueAsString(snapshot);
	}
	public List<Requirement> requirements(String snapshot) {
		var result = new ArrayList<Requirement>();
		for (var fact : documents.read(snapshot).path("_requirements").path("facts")) {
			if (!"EXPLICIT".equals(fact.path("origin").asText()) || !TARGET.contains(fact.path("kind").asText())) continue;
			String value = fact.path("value").asText();
			if (value.isBlank()) continue;
			String meaning = value + " " + fact.path("evidence").asText();
			if ("PROJECT_PREFERENCE".equals(fact.path("kind").asText())
				&& meaning.matches("(?s).*(제한\\s*없|상관\\s*없|무관).*")) continue;
			if (meaning.matches("(?is).*(필요\\s*없|불필요|필수.{0,6}(아니|아님)|없어도|not required).*")) continue;
			if ("TARGET_SKILL".equals(fact.path("kind").asText())) {
				var selected = new ArrayList<String>();
				for (var stack : documents.read(snapshot).path("stacks")) selected.add(stack.asText());
				if (!java.util.Collections.disjoint(com.devmozip.backend.utils.AiCandidateMatchingUtils.values(selected, true),
					com.devmozip.backend.utils.AiCandidateMatchingUtils.values(List.of(value), true))) continue;
			}
			result.add(new Requirement("r" + result.size(), fact.path("kind").asText(), value,
				fact.path("evidence").asText(), fact.path("messageId").asLong()));
		}
		return List.copyOf(result);
	}
	public String text(String snapshot) {
		var text = documents.read(snapshot).path("_requirements").path("comparisonText");
		return text.isString() ? text.asText() : null;
	}
	public boolean same(String left, String right) {
		var a = documents.read(left).path("_requirements").path("facts");
		var b = documents.read(right).path("_requirements").path("facts");
		return (a.isMissingNode() && b.isEmpty()) || a.equals(b);
	}
}
