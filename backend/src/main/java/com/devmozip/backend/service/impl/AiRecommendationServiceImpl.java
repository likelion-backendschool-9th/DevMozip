package com.devmozip.backend.service.impl;

import com.devmozip.backend.service.AiRecommendationService;
import com.devmozip.backend.service.AiRequirementSnapshot;
import com.devmozip.backend.service.AiRequirementEvaluationService;
import com.devmozip.backend.config.AiScoringProperties;
import com.devmozip.backend.utils.AiCandidateMatchingUtils;
import com.devmozip.backend.service.AiRecommendationScoringService;

import com.devmozip.backend.dto.ai.internal.RecommendationResult;
import com.devmozip.backend.dto.ai.internal.RecommendationResult.Developer;
import com.devmozip.backend.dto.ai.internal.RecommendationResult.DurationComparison;
import com.devmozip.backend.dto.ai.internal.RecommendationResult.RoleCoverage;
import com.devmozip.backend.dto.ai.internal.RecommendationResult.SimilarProject;
import com.devmozip.backend.dto.ai.internal.SimilarProjectCandidate;
import com.devmozip.backend.dto.ai.UpdateConditionsRequest;
import com.devmozip.backend.repository.AiCandidateRepository;
import com.devmozip.backend.security.ai.CurrentUserProvider;
import java.time.DateTimeException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiRecommendationServiceImpl implements AiRecommendationService {
	private static final Comparator<Developer> DEVELOPERS = Comparator
		.comparing((Developer item) -> item.score().total()).reversed().thenComparingLong(Developer::id);
	private static final Comparator<SimilarProject> PROJECTS = Comparator
		.comparing((SimilarProject item) -> item.score().total()).reversed().thenComparingLong(SimilarProject::id);
	private final CurrentUserProvider currentUser;
	private final AiCandidateRepository candidates;
	private final AiRecommendationScoringService scoring;
	private final AiRequirementEvaluationService requirementsEvaluator;

	public AiRecommendationServiceImpl(CurrentUserProvider currentUser, AiCandidateRepository candidates,
		AiRecommendationScoringService scoring) {
		this(currentUser, candidates, scoring, null);
	}
	@org.springframework.beans.factory.annotation.Autowired
	public AiRecommendationServiceImpl(CurrentUserProvider currentUser, AiCandidateRepository candidates,
		AiRecommendationScoringService scoring, AiRequirementEvaluationService requirementsEvaluator) {
		this.requirementsEvaluator = requirementsEvaluator;
		this.currentUser = currentUser;
		this.candidates = candidates;
		this.scoring = scoring;
	}

	/** 본문 userId를 받지 않는다. 결과·메시지·사용량을 저장하지 않는 내부 진입점이다. */
	@Override
	public RecommendationResult recommend(UpdateConditionsRequest conditions, String message) {
		return recommend(conditions, message, List.of());
	}
	@Override
	public RecommendationResult recommend(UpdateConditionsRequest conditions, String message, List<AiRequirementSnapshot.Requirement> requirements) {
		long requester = currentUser.getCurrentUserId();
		Objects.requireNonNull(conditions, "추천 조건이 필요합니다.");
		if (message != null && message.length() > 10000) throw new IllegalArgumentException("비교 메시지는 10000자 이하로 입력해 주세요.");
		var developers = new ArrayList<Developer>();
		long[] eligibleCounts = new long[conditions.roles().size()];
		long cursor = 0;
		while (true) {
			var batch = candidates.developers(requester, cursor, AiScoringProperties.QUERY_BATCH_SIZE);
			if (batch.isEmpty()) break;
			var added = new ArrayList<Developer>();
			for (var candidate : batch) {
				// 공개 프로필 조회도 직군 없는 회원은 공개하지 않는다.
				if (candidate.jobCategories() == null || candidate.jobCategories().isEmpty()) continue;
				if (!AiCandidateMatchingUtils.careerMatches(conditions.career(), candidate.career())) continue;
				var matchedRoles = new ArrayList<String>();
				for (int i = 0; i < conditions.roles().size(); i++) {
					String requested = conditions.roles().get(i).name();
					if (AiCandidateMatchingUtils.hasRole(candidate.jobCategories(), requested)) {
						matchedRoles.add(requested);
						eligibleCounts[i]++;
					}
				}
				if (!conditions.roles().isEmpty() && matchedRoles.isEmpty()) continue;
				added.add(new Developer(candidate.id(), candidate.nickname(), candidate.tag(),
					List.copyOf(matchedRoles), scoring.developer(candidate, conditions)));
			}
			for (int offset = 0; offset < added.size(); offset += 4) {
				var chunk = added.subList(offset, Math.min(offset + 4, added.size()));
				if (requirements.isEmpty()) {
					for (var developer : chunk) if (relevant(developer.score(), false)) developers.add(developer);
					continue;
				}
				var inputs = chunk.stream().map(d -> {
					var c = batch.stream().filter(v -> v.id() == d.id()).findFirst().orElseThrow();
					String ref = "developer:" + c.id();
					return new AiRequirementEvaluationService.Candidate(ref, List.of(
						requirementsEvaluator.source(ref + ":skills", "skills", String.join(", ", c.skills() == null ? List.of() : c.skills())),
						requirementsEvaluator.source(ref + ":roles", "jobCategories", String.join(", ", c.jobCategories())),
						requirementsEvaluator.source(ref + ":career", "career", c.career()),
						requirementsEvaluator.source(ref + ":trustScore", "trustScore", c.trustScore() == null ? null : c.trustScore().toPlainString()),
						requirementsEvaluator.source(ref + ":summary", "summary", c.summary())));
				}).toList();
				var bases = new java.util.HashMap<String, RecommendationResult.Score>();
				chunk.forEach(d -> bases.put("developer:" + d.id(), d.score()));
				var evaluated = requirementsEvaluator.evaluate(inputs, requirements, bases);
				for (var d : chunk) {
					var score = requirementsEvaluator.combine(d.score(), evaluated.get("developer:" + d.id()));
					if (relevant(score, false)) developers.add(new Developer(d.id(), d.nickname(), d.tag(), d.matchedRoles(), score));
				}
			}
			trim(developers, DEVELOPERS);
			cursor = batch.getLast().id();
		}
		var projects = new ArrayList<SimilarProject>();
		cursor = 0;
		while (true) {
			var batch = candidates.projects(cursor, AiScoringProperties.QUERY_BATCH_SIZE);
			if (batch.isEmpty()) break;
			for (int offset = 0; offset < batch.size(); offset += 4) {
				var chunk = batch.subList(offset, Math.min(offset + 4, batch.size()));
				var bases = new java.util.HashMap<String, RecommendationResult.Score>();
				chunk.forEach(c -> bases.put("project:" + c.id(), scoring.project(c, conditions, requirements.isEmpty() ? message : null)));
				java.util.Map<String, com.devmozip.backend.dto.ai.RequirementEvaluation> evaluated = java.util.Map.of();
				if (!requirements.isEmpty()) {
					var inputs = chunk.stream().map(c -> {
						String ref = "project:" + c.id();
						return new AiRequirementEvaluationService.Candidate(ref, List.of(
							requirementsEvaluator.source(ref + ":title", "title", c.title()),
							requirementsEvaluator.source(ref + ":summary", "summary", c.summary()),
							requirementsEvaluator.source(ref + ":description", "description", c.description()),
							requirementsEvaluator.source(ref + ":skills", "techStacks", String.join(", ", c.techStacks() == null ? List.of() : c.techStacks()))));
					}).toList();
					evaluated = requirementsEvaluator.evaluate(inputs, requirements, bases);
				}
				for (var c : chunk) {
					var base = bases.get("project:" + c.id());
					var score = requirements.isEmpty() ? base : requirementsEvaluator.combine(base, evaluated.get("project:" + c.id()));
					if (relevant(score, true)) projects.add(new SimilarProject(c.id(), c.title(), score,
						duration(c, conditions.durationMonths())));
				}
			}
			trim(projects, PROJECTS);
			cursor = batch.getLast().id();
		}
		var coverage = new ArrayList<RoleCoverage>();
		for (int i = 0; i < conditions.roles().size(); i++) {
			var role = conditions.roles().get(i);
			coverage.add(new RoleCoverage(role.name(), role.count(), eligibleCounts[i],
				developers.stream().filter(developer -> developer.matchedRoles().contains(role.name())).count()));
		}
		var notices = new ArrayList<String>();
		notices.add("포트폴리오 연동 전 경험은 미산정이며 사용 가능한 평가 항목만 100점으로 환산합니다.");
		notices.add("기간은 날짜 비교 정보만 제공하며 필터·점수에 반영하지 않습니다.");
		if (developers.isEmpty()) notices.add("추천 조건을 충족하는 공개 개발자 후보가 없습니다.");
		if (projects.isEmpty()) notices.add("추천 가능한 공개 프로젝트가 없습니다.");
		return new RecommendationResult(List.copyOf(developers), List.copyOf(projects), List.copyOf(coverage), List.copyOf(notices));
	}

	/** 점수 하한 없이 요청과의 연결을 확인한다. 상태·경력·신뢰온도만으로 추천을 채우지 않는다. */
	private static boolean relevant(RecommendationResult.Score score, boolean project) {
		var fields = project ? java.util.Set.of("interests", "skills", "text") : java.util.Set.of("role", "skills");
		if (score.items().stream().anyMatch(item -> fields.contains(item.name())
			&& item.evaluation() == RecommendationResult.Evaluation.SCORED && item.points().signum() > 0)) return true;
		var evaluation = score.requirementEvaluation();
		if (evaluation == null || !evaluation.applied()) return false;
		return evaluation.assessments().stream().anyMatch(item ->
			(item.status() == com.devmozip.backend.dto.ai.RequirementEvaluation.Status.MET
				|| item.status() == com.devmozip.backend.dto.ai.RequirementEvaluation.Status.PARTIAL)
			&& item.source() != null && !item.source().isBlank()
			&& item.quote() != null && !item.quote().isBlank()
			&& item.field() != null && !java.util.Set.of("career", "trustScore").contains(item.field()));
	}

	private static <T> void trim(List<T> items, Comparator<T> comparator) {
		items.sort(comparator);
		if (items.size() > AiScoringProperties.RESULT_LIMIT) items.subList(AiScoringProperties.RESULT_LIMIT, items.size()).clear();
	}

	private static DurationComparison duration(SimilarProjectCandidate project, int months) {
		var start = project.developmentStart();
		var end = project.developmentEnd();
		if (start == null || end == null || end.isBefore(start)) return new DurationComparison(start, end, null, null);
		try {
			var expected = start.plusMonths(months);
			return new DurationComparison(start, end, expected, ChronoUnit.DAYS.between(expected, end));
		} catch (DateTimeException exception) {
			return new DurationComparison(start, end, null, null);
		}
	}
}
