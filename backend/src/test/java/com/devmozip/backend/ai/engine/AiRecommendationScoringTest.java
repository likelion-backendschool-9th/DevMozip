package com.devmozip.backend.ai.engine;

import com.devmozip.backend.dto.ai.*;
import com.devmozip.backend.dto.ai.internal.*;
import com.devmozip.backend.dto.ai.internal.RecommendationResult.Evaluation;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.entity.enums.RequiredCareer;
import com.devmozip.backend.service.AiRecommendationScoringService;
import com.devmozip.backend.service.impl.AiRecommendationScoringServiceImpl;
import com.devmozip.backend.config.AiScoringProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiRecommendationScoringTest {
	static AiScoringProperties weights() {
		return new AiScoringProperties(n("30"), n("30"), n("20"), n("10"), n("50"), n("35"), n("35"), n("20"), n("10"));
	}
	static BigDecimal n(String value) { return new BigDecimal(value); }
	static UpdateConditionsRequest conditions(List<RecruitmentRoleConditionDto> roles, List<String> stacks, RequiredCareer career) {
		return new UpdateConditionsRequest(List.of("교육"), stacks, roles, career, 1);
	}
	static UpdateConditionsRequest defaults() {
		return conditions(List.of(new RecruitmentRoleConditionDto("백엔드", 1)), List.of("Java", "React"), RequiredCareer.THREE_YEARS);
	}
	static DeveloperCandidate developer(long id, List<String> roles, List<String> stacks, String career, String temperature) {
		return new DeveloperCandidate(id, "candidate-" + id, "tag-" + id, roles, stacks, List.of("교육"), career,
			temperature == null ? null : n(temperature));
	}
	static SimilarProjectCandidate project(long id, ProjectStatus status) {
		return new SimilarProjectCandidate(id, "교육 플랫폼", "교육 플랫폼", List.of("Java", "React"), List.of("교육"),
			status, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28));
	}
	final AiRecommendationScoringService scoring = new AiRecommendationScoringServiceImpl(weights());

	@Test void partialSkillsAndBaseTemperatureProduceExactNormalizedScoreWithoutExperience() {
		var score = scoring.developer(developer(1, List.of("백엔드 개발자"), List.of("Java"), "3년 차", "36.5"), defaults());
		assertEquals(n("64.29"), score.total()); // (30 + 15 + 0) / 70 * 100
		var experience = score.items().stream().filter(item -> item.name().equals("experience")).findFirst().orElseThrow();
		assertEquals(Evaluation.UNASSESSED, experience.evaluation());
		assertTrue(score.basicReasons().stream().anyMatch(reason -> reason.contains("경험은 미산정")));
		assertFalse(score.items().stream().anyMatch(item -> item.name().equals("career") || item.name().equals("availability")));
	}

	@Test void unknownCandidateSkillsAreZeroWhileUnselectedConditionsAreExcluded() {
		var candidate = developer(1, List.of("백엔드 개발자"), null, "3년차", "50");
		assertEquals(n("57.14"), scoring.developer(candidate, defaults()).total());
		assertEquals(n("100.00"), scoring.developer(candidate, conditions(List.of(), List.of(), RequiredCareer.ANY)).total());
		assertEquals(n("25.00"), scoring.developer(candidate, conditions(List.of(), List.of("Java"), RequiredCareer.ANY)).total());
	}

	@Test void trustIsLinearBetween365And50AndClampedOutsideRange() {
		var conditions = conditions(List.of(), List.of(), RequiredCareer.ANY);
		String[] inputs = {"-10", "36.5", "43.25", "50", "80"};
		String[] expected = {"0.00", "0.00", "50.00", "100.00", "100.00"};
		for (int i = 0; i < inputs.length; i++) assertEquals(n(expected[i]), scoring.developer(
			developer(1, List.of("백엔드 개발자"), List.of(), null, inputs[i]), conditions).total());
		assertEquals(n("0.00"), scoring.developer(developer(1, List.of("백엔드 개발자"), null, null, null), conditions).total());
	}

	@Test void roleScoreUsesRequestedPeopleWithoutTruncatingCandidatesAndDoesNotInferUnknownRoles() {
		var conditions = conditions(List.of(new RecruitmentRoleConditionDto("백엔드", 3),
			new RecruitmentRoleConditionDto("프론트엔드", 1)), List.of(), RequiredCareer.ANY);
		assertEquals(n("56.25"), scoring.developer(developer(1, List.of("BACKEND"), null, null, "36.5"), conditions).total());
		assertEquals(n("18.75"), scoring.developer(developer(2, List.of("프론트엔드 개발자"), null, null, "36.5"), conditions).total());
		assertEquals(n("0.00"), scoring.developer(developer(3, List.of("해양 백엔드 전문가"), null, null, "36.5"), conditions).total());
	}

	@Test void configuredWeightsChangeFormulaWithoutChangingAlgorithm() {
		var changed = new AiScoringProperties(n("10"), n("50"), n("20"), n("10"), n("50"), n("35"), n("35"), n("20"), n("10"));
		var result = new AiRecommendationScoringServiceImpl(changed).developer(
			developer(1, List.of("백엔드 개발자"), List.of("Java"), null, "36.5"), defaults());
		assertEquals(n("50.00"), result.total()); // (10 + 25) / 70
		assertThrows(IllegalArgumentException.class, () -> new AiScoringProperties(n("0"), n("30"), n("20"), n("10"),
			n("50"), n("35"), n("35"), n("20"), n("10")));
	}

	@Test void projectScoresHaveExpectedWeightsAndStatusOrdering() {
		String[] totals = {"100.00", "95.00", "93.00", "90.00"};
		var statuses = ProjectStatus.values();
		for (int i = 0; i < statuses.length; i++) assertEquals(n(totals[i]),
			scoring.project(project(1, statuses[i]), defaults(), "교육 플랫폼").total());
	}

	@Test void textUsesTokenJaccardAndNeverExtractsConditionsFromMessage() {
		var project = new SimilarProjectCandidate(1, "교육 플랫폼", "React", List.of("Java"), List.of("교육"),
			ProjectStatus.RECRUITING, null, null);
		assertEquals(n("67.50"), scoring.project(project, defaults(), "교육 커뮤니티").total());
		// interest 35 + skills 17.5 + text 20*(1/4) + status 10
		var exact = scoring.project(project(1, ProjectStatus.RECRUITING), defaults(), "<p>교육 플랫폼</p>");
		assertEquals(n("100.00"), exact.total());
	}

	@Test void emptyMessageAndOptionalStacksAreNotMissingCandidateData() {
		var request = conditions(List.of(), List.of(), RequiredCareer.ANY);
		var result = scoring.project(project(1, ProjectStatus.RECRUITING), request, null);
		assertEquals(n("100.00"), result.total()); // 45/45
		assertEquals(Evaluation.NOT_REQUESTED, result.items().stream().filter(item -> item.name().equals("text")).findFirst().orElseThrow().evaluation());
		var missing = new SimilarProjectCandidate(2, null, null, null, null, null, null, null);
		assertEquals(n("0.00"), scoring.project(missing, defaults(), "교육").total());
		assertFalse(scoring.project(missing, defaults(), "교육").basicReasons().isEmpty());
	}

	@Test void aliasesNormalizeCaseAndDeduplicateCandidateTechnologies() {
		assertEquals(n("100.00"), scoring.developer(developer(1, List.of("BACKEND"),
			List.of("java", "React.js", "ReactJS"), null, "50"), defaults()).total());
	}
}
