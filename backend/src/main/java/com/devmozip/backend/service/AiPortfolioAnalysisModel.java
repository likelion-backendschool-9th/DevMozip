package com.devmozip.backend.service;

import com.devmozip.backend.client.GeminiAnalysisClient;
import com.devmozip.backend.config.GeminiProperties;
import com.devmozip.backend.dto.ai.PortfolioAnalysisResponse.Assessment;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** 모델은 근거를 분류한다. 점수와 순위를 생성하거나 수정하지 않는다. */
@Component
public class AiPortfolioAnalysisModel {
	public record Document(long id, String title, String summary, String role, String skills, String description) {}
	private final GeminiAnalysisClient gemini;
	private final GeminiProperties properties;
	private final ObjectMapper mapper;
	private final String instruction;
	private final tools.jackson.databind.JsonNode schema;
	public AiPortfolioAnalysisModel(GeminiAnalysisClient gemini, GeminiProperties properties, ObjectMapper mapper) throws java.io.IOException {
		this.gemini = gemini; this.properties = properties; this.mapper = mapper;
		try (var stream = new ClassPathResource("ai/portfolio-instruction.txt").getInputStream()) {
			instruction = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		}
		try (var stream = new ClassPathResource("ai/portfolio-schema.json").getInputStream()) { schema = mapper.readTree(stream); }
	}
	public String version() { return "portfolio-v1:" + properties.getModel(); }
	public List<Assessment> analyze(List<AiPortfolioSelection.Criterion> criteria, List<Document> documents, long deadline) {
		var result = new ArrayList<Assessment>();
		for (int offset = 0; offset < criteria.size(); offset += 8) {
			var timeout = properties.getTimeout();
			if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(java.time.Duration.ofSeconds(60)) > 0)
				throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 호출 제한 시간을 확인해 주세요.");
			if (deadline - System.nanoTime() <= timeout.toNanos())
				throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "상세 분석 전체 제한 시간이 초과되었습니다. 다시 시도해 주세요.");
			var batch = criteria.subList(offset, Math.min(offset + 8, criteria.size()));
			var output = gemini.generate(mapper.writeValueAsString(Map.of("requirements", batch, "portfolios", documents)), instruction, schema);
			if (output == null || !output.path("assessments").isArray() || output.path("assessments").size() > batch.size()) throw invalid();
			var seen = new HashSet<String>();
			var found = new java.util.HashMap<String, Assessment>();
			for (var row : output.path("assessments")) {
				String id = row.path("requirementId").asText();
				var criterion = batch.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow(AiPortfolioAnalysisModel::invalid);
				if (!seen.add(id)) throw invalid();
				String status = row.path("status").asText();
				if (!Set.of("MET", "PARTIAL", "MISMATCH", "UNKNOWN").contains(status)) throw invalid();
				long portfolioId = row.path("portfolioId").asLong();
				String field = row.path("field").asText();
				String quote = row.path("quote").asText();
				var source = documents.stream().filter(d -> d.id() == portfolioId).findFirst();
				if (!status.equals("UNKNOWN") && source.isEmpty()) throw invalid();
				String text = source.map(d -> switch (field) {
					case "title" -> d.title(); case "summary" -> d.summary(); case "role" -> d.role();
					case "skills" -> d.skills(); case "description" -> d.description(); default -> "";
				}).orElse("");
				// 정형 기술/역할은 실제 기능 구현 경험의 증거가 아니다.
				boolean experience = !criterion.kind().equals("FORMAL") && !criterion.kind().equals("TARGET_SKILL")
					&& !Set.of("description", "summary").contains(field);
				if (status.equals("UNKNOWN") || quote.isBlank() || quote.length() > 500 || !text.contains(quote) || experience) {
					found.put(id, unknown(criterion));
				} else found.put(id, new Assessment(id, criterion.value(), status, portfolioId, field, quote));
			}
			for (var criterion : batch) result.add(found.getOrDefault(criterion.id(), unknown(criterion)));
		}
		return List.copyOf(result);
	}
	private Assessment unknown(AiPortfolioSelection.Criterion criterion) {
		return new Assessment(criterion.id(), criterion.value(), "UNKNOWN", null, null, null);
	}
	private static ResponseStatusException invalid() {
		return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "포트폴리오 분석 응답의 출처와 형식을 확인할 수 없습니다.");
	}
}
