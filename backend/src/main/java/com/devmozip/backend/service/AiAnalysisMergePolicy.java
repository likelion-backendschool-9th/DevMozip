package com.devmozip.backend.service;

import com.devmozip.backend.dto.ai.GeminiAnalysis;
import com.devmozip.backend.dto.ai.GeminiAnalysis.*;
import com.devmozip.backend.dto.ai.UpdateConditionsRequest;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** 모델의 제안을 검증하고 명시한 변경만 적용한다. 후보/점수/사용량에는 접근하지 않는다. */
@Component
public class AiAnalysisMergePolicy {
	private final ObjectMapper mapper;
	private final AiConditionsDocument documents;
	public AiAnalysisMergePolicy(ObjectMapper mapper, AiConditionsDocument documents) { this.mapper = mapper; this.documents = documents; }
	public record Merged(String document, String reply) {}
	public Merged merge(String previous, GeminiAnalysis analysis, String latest, long messageId) {
		ObjectNode doc = documents.read(previous);
		ObjectNode initial = documents.formal(doc);
		ObjectNode metadata = doc.has("_analysis") ? doc.withObject("_analysis").deepCopy() : mapper.createObjectNode();
		var sources = metadata.withObject("sources");
		for (String field : AiConditionsDocument.FIELDS) if (doc.has(field) && !sources.has(field)) {
			sources.set(field, mapper.valueToTree(Map.of("origin", "MANUAL")));
		}
		var questions = new LinkedHashSet<>(analysis.questions());
		var changedFields = new LinkedHashSet<Field>();
		var suggestions = new ArrayList<Change>();
		var factChanges = new ArrayList<>(analysis.facts());
		var resolvedRoles = new HashSet<String>();
		for (var change : analysis.changes()) {
			evidence(latest, change.evidence());
			if (change.field() == Field.roles && (change.action() == Action.ADD || change.action() == Action.SET)
				&& change.count() == null) {
				String role = change.values().getFirst().strip();
				boolean alreadyAsked = questions.stream().anyMatch(q -> asksCount(q, role)) || asksCount(analysis.reply(), role);
				if (!alreadyAsked) questions.add(role + " 동료는 몇 명 정도 함께하길 원하시나요?");
				factChanges.add(new FactChange(Kind.OTHER, Action.ADD, role + " (인원 미정)",
					change.origin(), change.evidence()));
				continue;
			}
			boolean ownTechnology = change.field() == Field.stacks && analysis.facts().stream().anyMatch(f ->
				(f.kind() == Kind.OWN_SKILL || f.kind() == Kind.LEARNING_SKILL) && f.evidence().equals(change.evidence()));
			boolean pendingRoleAnswer = change.field() == Field.roles && change.count() != null
				&& (change.action() == Action.ADD || change.action() == Action.SET)
				&& metadata.path("facts").isArray()
				&& java.util.stream.StreamSupport.stream(metadata.path("facts").spliterator(), false).anyMatch(f ->
					"OTHER".equals(f.path("kind").asText()) && "EXPLICIT".equals(f.path("origin").asText())
					&& (change.values().getFirst().strip() + " (인원 미정)").equals(f.path("value").asText()));
			if (change.origin() == Origin.INFERRED || ownTechnology
				|| doc.has(change.field().name()) && !change.explicitChange() && !pendingRoleAnswer && !unchanged(doc, change)) {
				suggestions.add(change);
				questions.add(label(change.field()) + " 조건 변경을 원하시나요? 변경할 값을 명시해 주세요.");
				continue;
			}
			if (unchanged(doc, change)) continue;
			apply(doc, change);
			normalizePartial(doc);
			if (change.field() == Field.roles && change.count() != null
				&& (change.action() == Action.ADD || change.action() == Action.SET)) {
				resolvedRoles.add(change.values().getFirst().strip() + " (인원 미정)");
			}
			sources.set(change.field().name(), mapper.valueToTree(Map.of("origin", "EXPLICIT", "messageId", messageId, "evidence", change.evidence())));
			changedFields.add(change.field());
		}
		var applied = changedFields.stream()
			.filter(field -> !Objects.equals(initial.get(field.name()), doc.get(field.name())))
			.map(field -> label(field) + ": " + display(doc, field)).toList();
		var facts = new ArrayList<ObjectNode>();
		if (metadata.path("facts").isArray()) for (var fact : metadata.path("facts")) facts.add((ObjectNode) fact.deepCopy());
		for (var change : factChanges) {
			evidence(latest, change.evidence());
			if (change.origin() == Origin.INFERRED && change.action() != Action.ADD) {
				questions.add("이전 요구사항을 변경하려면 변경할 내용을 명시해 주세요.");
				continue;
			}
			String kind = change.kind().name();
			if (change.action() == Action.SET || change.action() == Action.CLEAR) facts.removeIf(f -> kind.equals(f.path("kind").asText()));
			if (change.action() == Action.REMOVE) facts.removeIf(f -> kind.equals(f.path("kind").asText()) && change.value().equals(f.path("value").asText()));
			if ((change.action() == Action.ADD || change.action() == Action.SET) && facts.stream().noneMatch(f ->
				kind.equals(f.path("kind").asText()) && change.value().equals(f.path("value").asText()) && change.origin().name().equals(f.path("origin").asText()))) {
				facts.add(mapper.valueToTree(Map.of("kind", kind, "value", change.value(), "origin", change.origin(),
					"evidence", change.evidence(), "messageId", messageId)));
			}
		}
		facts.removeIf(f -> "OTHER".equals(f.path("kind").asText()) && resolvedRoles.contains(f.path("value").asText()));
		if (facts.size() > 100 || facts.stream().mapToInt(f -> f.path("value").asText().length()).sum() > 12000) {
			throw new IllegalArgumentException("보존 가능한 요구사항 수를 초과했습니다. 새 대화를 시작해 주세요.");
		}
		metadata.put("version", 1);
		metadata.put("lastMessageId", messageId);
		metadata.set("facts", mapper.valueToTree(facts));
		metadata.set("suggestions", mapper.valueToTree(suggestions));
		metadata.set("appliedChanges", mapper.valueToTree(applied));
		metadata.set("questions", mapper.valueToTree(questions));
		doc.set("_analysis", metadata);
		var remainingQuestions = questions.stream().filter(q -> !compact(analysis.reply()).contains(compact(q))).toList();
		String reply = analysis.reply() + (applied.isEmpty() ? "" : "\n\n반영된 조건\n" + String.join("\n", applied))
			+ (remainingQuestions.isEmpty() ? "" : "\n\n확인할 내용\n" + String.join("\n", remainingQuestions));
		return new Merged(mapper.writeValueAsString(doc), reply);
	}
	private static String compact(String text) { return text.replaceAll("[\\s\\p{P}]+", ""); }
	private static boolean asksCount(String text, String role) {
		return text.contains(role) && (text.contains("몇") || text.contains("인원")) && (text.contains("명") || text.contains("인원"));
	}
	private void normalizePartial(ObjectNode doc) {
		var formal = documents.formal(doc);
		var absent = new HashSet<String>();
		// 기존 DTO의 값/개수 검증을 재사용하기 위한 임시값. 저장/응답에는 넣지 않는다.
		for (String field : List.of("interests", "career", "durationMonths")) if (!formal.has(field)) absent.add(field);
		if (absent.contains("interests")) formal.set("interests", mapper.valueToTree(List.of("웹")));
		if (absent.contains("career")) formal.put("career", "경력 무관");
		if (absent.contains("durationMonths")) formal.put("durationMonths", 1);
		ObjectNode normalized = mapper.valueToTree(mapper.treeToValue(formal, UpdateConditionsRequest.class));
		for (String field : AiConditionsDocument.FIELDS) doc.remove(field);
		absent.forEach(normalized::remove);
		doc.setAll(normalized);
	}
	private boolean unchanged(ObjectNode doc, Change c) {
		if (c.action() != Action.SET) return false;
		var copy = doc.deepCopy();
		apply(copy, c);
		return Objects.equals(doc.get(c.field().name()), copy.get(c.field().name()));
	}
	private void apply(ObjectNode doc, Change c) {
		String key = c.field().name();
		if (c.action() == Action.CLEAR) { doc.remove(key); return; }
		if (c.field() == Field.roles) {
			var roles = new ArrayList<ObjectNode>();
			if (doc.path(key).isArray()) for (var role : doc.path(key)) roles.add((ObjectNode) role.deepCopy());
			String name = c.values().getFirst().strip();
			var old = roles.stream().filter(r -> name.equalsIgnoreCase(r.path("name").asText())).findFirst();
			int count = c.count() == null ? 0 : c.count();
			if (c.action() == Action.ADD && old.isPresent()) count += old.get().path("count").asInt();
			roles.removeIf(r -> name.equalsIgnoreCase(r.path("name").asText()));
			if (c.action() != Action.REMOVE) roles.add(mapper.valueToTree(Map.of("name", name, "count", count)));
			doc.set(key, mapper.valueToTree(roles));
		} else if (c.field() == Field.interests || c.field() == Field.stacks) {
			var values = new LinkedHashSet<String>();
			if (c.action() != Action.SET && doc.path(key).isArray()) for (var v : doc.path(key)) values.add(v.asText());
			if (c.action() == Action.REMOVE) values.removeIf(v -> c.values().stream().anyMatch(x -> x.strip().equalsIgnoreCase(v)));
			else values.addAll(c.values().stream().map(String::strip).toList());
			if (values.isEmpty()) doc.remove(key); else doc.set(key, mapper.valueToTree(values));
		} else if (c.action() == Action.REMOVE) doc.remove(key);
		else if (c.field() == Field.durationMonths) {
			if (!c.values().getFirst().matches("[1-9][0-9]?")) throw new IllegalArgumentException("기간 형식 오류");
			doc.put(key, Integer.parseInt(c.values().getFirst()));
		} else doc.put(key, c.values().getFirst());
	}
	private static void evidence(String latest, String evidence) {
		if (!latest.contains(evidence)) throw new IllegalArgumentException("현재 메시지에서 근거를 확인할 수 없습니다.");
	}
	private static String label(Field field) {
		return switch (field) { case interests -> "관심 분야"; case stacks -> "요구 기술"; case roles -> "모집 직군";
			case career -> "요구 경력"; case durationMonths -> "예상 기간"; };
	}
	private static String display(ObjectNode doc, Field field) {
		var value = doc.get(field.name());
		if (value == null) return "선택 해제";
		if (field == Field.durationMonths) return value.asInt() + "개월";
		if (field == Field.career) return value.asText();
		var values = new ArrayList<String>();
		for (var item : value) values.add(field == Field.roles ? item.path("name").asText() + " " + item.path("count").asInt() + "명" : item.asText());
		return String.join(", ", values);
	}
}
