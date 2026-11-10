package com.devmozip.backend.service.impl;

import com.devmozip.backend.service.AiRecommendationScoringService;
import com.devmozip.backend.config.AiScoringProperties;
import com.devmozip.backend.utils.AiCandidateMatchingUtils;

import com.devmozip.backend.dto.ai.internal.DeveloperCandidate;
import com.devmozip.backend.dto.ai.internal.RecommendationResult.Evaluation;
import com.devmozip.backend.dto.ai.internal.RecommendationResult.Score;
import com.devmozip.backend.dto.ai.internal.RecommendationResult.ScoreItem;
import com.devmozip.backend.dto.ai.internal.SimilarProjectCandidate;
import com.devmozip.backend.dto.ai.UpdateConditionsRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** 후보 필터·영속성 접근과 분리된 규칙 기반 계산이다. */
@Service
public class AiRecommendationScoringServiceImpl implements AiRecommendationScoringService {
	private static final BigDecimal HUNDRED = new BigDecimal("100");
	private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}+#.]+");
	private final AiScoringProperties weights;

	public AiRecommendationScoringServiceImpl(AiScoringProperties weights) { this.weights = weights; }

	@Override
	public Score developer(DeveloperCandidate candidate, UpdateConditionsRequest conditions) {
		var items = new ArrayList<ScoreItem>();
		var reasons = new ArrayList<String>();
		boolean roleRequested = !conditions.roles().isEmpty();
		int requestedPeople = conditions.roles().stream().mapToInt(role -> role.count()).sum();
		int matchedPeople = conditions.roles().stream()
			.filter(role -> AiCandidateMatchingUtils.hasRole(candidate.jobCategories(), role.name()))
			.mapToInt(role -> role.count()).sum();
		items.add(item("role", weights.roleWeight(), roleRequested, ratio(matchedPeople, requestedPeople)));
		Set<String> requested = AiCandidateMatchingUtils.values(conditions.stacks(), true);
		Set<String> matching = intersection(requested, AiCandidateMatchingUtils.values(candidate.skills(), true));
		items.add(item("skills", weights.skillWeight(), !requested.isEmpty(), ratio(matching.size(), requested.size())));
		if (!matching.isEmpty()) {
			var labels = candidate.skills().stream().filter(skill -> !java.util.Collections.disjoint(
				AiCandidateMatchingUtils.values(java.util.Collections.singletonList(skill), true), matching)).map(String::strip).distinct().toList();
			reasons.add(String.join(", ", labels) + " 기술 스택을 활용할 수 있습니다.");
		}
		items.add(new ScoreItem("experience", Evaluation.UNASSESSED, weights.experienceWeight(), BigDecimal.ZERO));
		BigDecimal trustRatio = candidate.trustScore() == null ? BigDecimal.ZERO : candidate.trustScore()
			.subtract(AiScoringProperties.BASE_TEMPERATURE)
			.divide(weights.trustCeiling().subtract(AiScoringProperties.BASE_TEMPERATURE), 12, RoundingMode.HALF_UP);
		items.add(item("trust", weights.trustWeight(), true, trustRatio));
		Set<String> interests = intersection(AiCandidateMatchingUtils.values(conditions.interests(), false),
			AiCandidateMatchingUtils.values(candidate.interests(), false));
		if (!interests.isEmpty()) reasons.add("관심 분야가 " + interests.size() + "개 일치합니다(가산점 없음).");
		reasons.add("협업 가능한 상태이며 요구 경력 필터를 통과했습니다.");
		reasons.add("포트폴리오 연동 전 경험은 미산정이며, 평가 가능한 항목을 100점으로 환산했습니다.");
		return finish(items, reasons);
	}

	@Override
	public Score project(SimilarProjectCandidate candidate, UpdateConditionsRequest conditions, String message) {
		var items = new ArrayList<ScoreItem>();
		var reasons = new ArrayList<String>();
		Set<String> interests = AiCandidateMatchingUtils.values(conditions.interests(), false);
		Set<String> sharedInterests = intersection(interests, AiCandidateMatchingUtils.values(candidate.interests(), false));
		items.add(item("interests", weights.projectInterestWeight(), true, ratio(sharedInterests.size(), interests.size())));
		if (!sharedInterests.isEmpty()) reasons.add("관심 분야가 " + sharedInterests.size() + "개 일치합니다.");
		Set<String> stacks = AiCandidateMatchingUtils.values(conditions.stacks(), true);
		Set<String> sharedStacks = intersection(stacks, AiCandidateMatchingUtils.values(candidate.techStacks(), true));
		items.add(item("skills", weights.projectSkillWeight(), !stacks.isEmpty(), ratio(sharedStacks.size(), stacks.size())));
		if (!sharedStacks.isEmpty()) reasons.add("기술 스택이 " + sharedStacks.size() + "개 일치합니다.");
		Set<String> query = tokens(message);
		Set<String> document = tokens((candidate.title() == null ? "" : candidate.title()) + " "
			+ (candidate.description() == null ? "" : candidate.description()));
		Set<String> union = new TreeSet<>(query);
		union.addAll(document);
		BigDecimal similarity = ratio(intersection(query, document).size(), union.size());
		items.add(item("text", weights.projectTextWeight(), !query.isEmpty(), similarity));
		if (similarity.signum() > 0) reasons.add("입력 메시지와 프로젝트 제목·설명에 공통 표현이 있습니다.");
		BigDecimal status = candidate.status() == null ? BigDecimal.ZERO : switch (candidate.status()) {
			case RECRUITING -> BigDecimal.ONE;
			case RECRUITMENT_COMPLETED -> AiScoringProperties.RECRUITMENT_COMPLETED_RATIO;
			case IN_PROGRESS -> AiScoringProperties.IN_PROGRESS_RATIO;
			case COMPLETED -> BigDecimal.ZERO;
		};
		items.add(item("status", weights.projectStatusWeight(), true, status));
		if (candidate.status() != null) reasons.add("모집·진행 상태: " + candidate.status().name());
		if (query.isEmpty()) reasons.add("비교 메시지가 없어 텍스트 항목을 제외하고 환산했습니다.");
		if (sharedInterests.isEmpty() && sharedStacks.isEmpty() && similarity.signum() == 0) {
			reasons.add("조건과 일치하는 분야·기술·텍스트 정보가 부족합니다.");
		}
		return finish(items, reasons);
	}

	private static ScoreItem item(String name, BigDecimal weight, boolean requested, BigDecimal fraction) {
		return new ScoreItem(name, requested ? Evaluation.SCORED : Evaluation.NOT_REQUESTED, weight,
			requested ? weight.multiply(fraction.max(BigDecimal.ZERO).min(BigDecimal.ONE)) : BigDecimal.ZERO);
	}

	private static Score finish(List<ScoreItem> items, List<String> reasons) {
		BigDecimal denominator = items.stream().filter(item -> item.evaluation() == Evaluation.SCORED)
			.map(ScoreItem::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal points = items.stream().map(ScoreItem::points).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal total = denominator.signum() == 0 ? BigDecimal.ZERO : points.multiply(HUNDRED)
			.divide(denominator, 2, RoundingMode.HALF_UP);
		return new Score(total.max(BigDecimal.ZERO).min(HUNDRED).setScale(2, RoundingMode.HALF_UP),
			items.stream().map(item -> new ScoreItem(item.name(), item.evaluation(), item.weight(),
				item.points().setScale(2, RoundingMode.HALF_UP))).toList(), List.copyOf(reasons));
	}

	private static BigDecimal ratio(int matches, int count) {
		return count == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(matches)
			.divide(BigDecimal.valueOf(count), 12, RoundingMode.HALF_UP);
	}

	private static Set<String> intersection(Set<String> first, Set<String> second) {
		var common = new TreeSet<>(first);
		common.retainAll(second);
		return common;
	}

	private static Set<String> tokens(String input) {
		var result = new TreeSet<String>();
		var matcher = TOKEN.matcher(AiCandidateMatchingUtils.text(input).replaceAll("<[^>]*>", " "));
		while (matcher.find()) result.add(matcher.group());
		return result;
	}
}
