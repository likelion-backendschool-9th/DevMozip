package com.devmozip.backend.service.impl;

import com.devmozip.backend.service.AiRecommendationService;
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

	public AiRecommendationServiceImpl(CurrentUserProvider currentUser, AiCandidateRepository candidates,
		AiRecommendationScoringService scoring) {
		this.currentUser = currentUser;
		this.candidates = candidates;
		this.scoring = scoring;
	}

	/** 본문 userId를 받지 않는다. 결과·메시지·사용량을 저장하지 않는 내부 진입점이다. */
	@Override
	@Transactional(readOnly = true)
	public RecommendationResult recommend(UpdateConditionsRequest conditions, String message) {
		long requester = currentUser.getCurrentUserId();
		Objects.requireNonNull(conditions, "추천 조건이 필요합니다.");
		if (message != null && message.length() > 10000) throw new IllegalArgumentException("비교 메시지는 10000자 이하로 입력해 주세요.");
		var developers = new ArrayList<Developer>();
		long[] eligibleCounts = new long[conditions.roles().size()];
		long cursor = 0;
		while (true) {
			var batch = candidates.developers(requester, cursor, AiScoringProperties.QUERY_BATCH_SIZE);
			if (batch.isEmpty()) break;
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
				developers.add(new Developer(candidate.id(), candidate.nickname(), candidate.tag(),
					List.copyOf(matchedRoles), scoring.developer(candidate, conditions)));
			}
			trim(developers, DEVELOPERS);
			cursor = batch.getLast().id();
		}
		var projects = new ArrayList<SimilarProject>();
		cursor = 0;
		while (true) {
			var batch = candidates.projects(cursor, AiScoringProperties.QUERY_BATCH_SIZE);
			if (batch.isEmpty()) break;
			for (var candidate : batch) projects.add(new SimilarProject(candidate.id(), candidate.title(),
				scoring.project(candidate, conditions, message), duration(candidate, conditions.durationMonths())));
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
