package com.devmozip.backend.service;

import com.devmozip.backend.repository.AiPortfolioAnalysisRepository.Metadata;
import com.devmozip.backend.utils.AiCandidateMatchingUtils;
import com.devmozip.backend.utils.AiInputPrivacy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 전체 공개 메타데이터에서 설명 가능한 어휘 관련도를 계산한다. 본문은 선택 후에만 읽는다. */
@Component
public class AiPortfolioSelection {
	public record Criterion(String id, String value, String kind) {}
	public record Selected(Metadata metadata, int relevance, List<String> matched) {}
	private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}+#.]{2,}");
	private static final Set<String> STOP = Set.of("구현", "경험", "개발", "개발자", "기능", "프로젝트", "선호", "필수",
		"있는", "있음", "사용자", "이상", "이하", "초과", "미만", "함께", "원하는", "가능한");
	private final AiRequirementSnapshot snapshots;
	private final ObjectMapper mapper;
	public AiPortfolioSelection(AiRequirementSnapshot snapshots, ObjectMapper mapper) { this.snapshots = snapshots; this.mapper = mapper; }
	public List<Criterion> criteria(String snapshot) {
		var result = new ArrayList<Criterion>();
		for (var requirement : snapshots.requirements(snapshot)) result.add(new Criterion(requirement.id(), clean(requirement.value(), 500), requirement.kind()));
		var document = mapper.readTree(snapshot);
		for (String key : List.of("stacks", "roles", "interests")) {
			for (var value : document.path(key)) {
				String text = key.equals("roles") ? value.path("name").asText() : value.asText();
				if (!text.isBlank() && result.stream().noneMatch(c -> c.value().equals(text)))
					result.add(new Criterion("formal-" + key + "-" + result.size(), clean(text, 100), "FORMAL"));
			}
		}
		return List.copyOf(result);
	}
	public List<Selected> choose(List<Metadata> metadata, List<Criterion> criteria) {
		var selected = new ArrayList<Selected>();
		for (var row : metadata) {
			String text = AiCandidateMatchingUtils.text(clean(row.title(), 1000) + " " + clean(row.summary(), 3000)
				+ " " + clean(row.role(), 500) + " " + clean(row.skills(), 3000));
			var matched = new ArrayList<String>();
			int score = 0;
			for (var criterion : criteria) {
				int hits = (int) words(criterion.value()).stream().filter(text::contains).count();
				if (hits > 0) {
					score += hits * (criterion.id().startsWith("formal-") ? 1 : 4);
					matched.add(criterion.value());
				}
			}
			if (score > 0) selected.add(new Selected(row, score, List.copyOf(matched)));
		}
		selected.sort(Comparator.comparingInt(Selected::relevance).reversed().thenComparingLong(s -> s.metadata().id()));
		return List.copyOf(selected.subList(0, Math.min(5, selected.size())));
	}
	private Set<String> words(String value) {
		var result = new LinkedHashSet<String>();
		var matcher = WORD.matcher(AiCandidateMatchingUtils.text(value));
		while (matcher.find()) {
			String word = matcher.group().replaceFirst("(?:에서는|으로|에서|이상이|이하인|이상인|있는|경험이|경험을|을|를|의|인)$", "");
			if (word.length() >= 2 && !STOP.contains(word) && !word.matches("[0-9.]+(?:도)?")) result.add(word);
		}
		return result;
	}
	public String clean(String value, int max) {
		String clean = AiInputPrivacy.redact(value == null ? "" : value.replaceAll("<[^>]*>", " "));
		return clean.substring(0, Math.min(max, clean.length()));
	}
}
