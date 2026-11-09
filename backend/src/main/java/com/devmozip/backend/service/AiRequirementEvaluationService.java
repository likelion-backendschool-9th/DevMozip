package com.devmozip.backend.service;

import com.devmozip.backend.client.GeminiAnalysisClient;
import com.devmozip.backend.dto.ai.RequirementEvaluation;
import com.devmozip.backend.dto.ai.RequirementEvaluation.*;
import com.devmozip.backend.dto.ai.internal.RecommendationResult.Score;
import com.devmozip.backend.utils.AiInputPrivacy;
import java.math.*;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 공개 조회 자료의 근거를 모델이 분류하고, 서버가 식별자·인용·점수를 검증한다. */
@Service
public class AiRequirementEvaluationService {
	public record Source(String ref, String field, String text) {}
	public record Candidate(String ref, List<Source> sources) {}
	private final GeminiAnalysisClient gemini;
	private final ObjectMapper mapper;
	private final tools.jackson.databind.JsonNode schema;
	private final String instruction;
	public AiRequirementEvaluationService(GeminiAnalysisClient gemini, ObjectMapper mapper) throws java.io.IOException {
		this.gemini = gemini; this.mapper = mapper;
		try (var input = new ClassPathResource("ai/requirements-schema.json").getInputStream()) { schema = mapper.readTree(input); }
		try (var input = new ClassPathResource("ai/requirements-instruction.txt").getInputStream()) {
			instruction = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		}
	}
	public Source source(String ref, String field, String text) {
		String clean = AiInputPrivacy.redact(text == null ? "" : text.replaceAll("<[^>]*>", " "));
		return new Source(ref, field, clean.substring(0, Math.min(3000, clean.length())));
	}
	public Map<String, RequirementEvaluation> evaluate(List<Candidate> candidates, List<AiRequirementSnapshot.Requirement> requirements,
		Map<String, Score> scores) {
		if (requirements.isEmpty() || candidates.isEmpty()) return Map.of();
		var result = new HashMap<String, RequirementEvaluation>();
		// 후보 배치는 호출자가 구성한다. 요구사항은 8개씩 나누어 비교한다.
		var found = new HashMap<String, Assessment>();
		for (int start = 0; start < requirements.size(); start += 8) {
			var requested = requirements.subList(start, Math.min(start + 8, requirements.size()));
			var payload = Map.of("requirements", requested, "candidates", candidates);
			var output = gemini.generate(AiInputPrivacy.redact(mapper.writeValueAsString(payload)), instruction, schema);
			if (output == null || !output.path("evaluations").isArray()) throw invalid();
			for (var row : output.path("evaluations")) {
				String candidateRef = row.path("candidate").asText();
				String requirementId = row.path("requirement").asText();
				var candidate = candidates.stream().filter(c -> c.ref().equals(candidateRef)).findFirst();
				if (candidate.isEmpty() || requested.stream().noneMatch(r -> r.id().equals(requirementId))) throw invalid();
				var requirement = requested.stream().filter(r -> r.id().equals(requirementId)).findFirst().orElseThrow();
				String key = candidateRef + ":" + requirementId;
				if (found.containsKey(key)) throw invalid();
				Status status;
				try { status = Status.valueOf(row.path("status").asText()); } catch (RuntimeException e) { throw invalid(); }
				String ref = row.path("source").asText();
				String quote = row.path("quote").asText();
				var source = candidate.get().sources().stream().filter(s -> s.ref().equals(ref)).findFirst();
				if (status == Status.UNKNOWN || source.isEmpty() || quote.isBlank() || quote.length() > 500
					|| !source.get().text().contains(quote) || !eligibleSource(requirement.kind(), source.get().field())) {
					found.put(key, new Assessment(requirementId, requirement.value(), requirement.mode(), Status.UNKNOWN, null, null, null));
				} else found.put(key, new Assessment(requirementId, requirement.value(), requirement.mode(), status, ref, source.get().field(), quote));
			}
		}
		for (var candidate : candidates) {
			var assessments = requirements.stream().map(r -> found.getOrDefault(candidate.ref() + ":" + r.id(),
				new Assessment(r.id(), r.value(), r.mode(), Status.UNKNOWN, null, null, null))).toList();
			BigDecimal points = assessments.stream().map(a -> switch (a.status()) {
				case MET -> new BigDecimal("100");
				case PARTIAL -> partialPoints();
				default -> BigDecimal.ZERO;
			}).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(assessments.size()), 2, RoundingMode.HALF_UP);
			var base = scores.get(candidate.ref());
			result.put(candidate.ref(), new RequirementEvaluation(1, true, base.total(), points, assessments, String.join("\n", base.basicReasons())));
		}
		return result;
	}
	private static boolean eligibleSource(String kind, String field) {
		if ("TARGET_SKILL".equals(kind)) return Set.of("skills", "techStacks", "description", "summary").contains(field);
		// 경력 연수·기술 목록만으로 기능 구현 경험이나 멘토링 역량을 인정하지 않는다.
		return Set.of("description", "title", "summary").contains(field);
	}
	private static BigDecimal partialPoints() {
		return new BigDecimal("50");
	}
	public Score combine(Score base, RequirementEvaluation evaluation) {
		if (evaluation == null) return base;
		BigDecimal total = base.total().multiply(new BigDecimal("0.8"))
			.add(evaluation.requirementScore().multiply(new BigDecimal("0.2"))).setScale(2, RoundingMode.HALF_UP);
		var reasons = new ArrayList<>(base.basicReasons());
		reasons.add("세부 요구사항을 20% 반영했습니다. 자료가 없는 항목은 미확인이며 경험 부재를 뜻하지 않습니다.");
		return new Score(total, base.items(), List.copyOf(reasons), evaluation);
	}
	private static org.springframework.web.server.ResponseStatusException invalid() {
		return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "추천 요구사항 분석 응답을 검증하지 못했습니다.");
	}
}
