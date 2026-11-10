package com.devmozip.backend.ai.engine;

import com.devmozip.backend.dto.ai.*;
import com.devmozip.backend.dto.ai.internal.*;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.entity.enums.RequiredCareer;
import com.devmozip.backend.repository.AiCandidateRepository;
import com.devmozip.backend.security.ai.CurrentUserProvider;
import com.devmozip.backend.service.AiRecommendationService;
import com.devmozip.backend.service.impl.AiRecommendationServiceImpl;
import com.devmozip.backend.service.AiRecommendationScoringService;
import com.devmozip.backend.service.impl.AiRecommendationScoringServiceImpl;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import static com.devmozip.backend.ai.engine.AiRecommendationScoringTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiRecommendationServiceTest {
	final AiCandidateRepository repository = mock(AiCandidateRepository.class);
	final CurrentUserProvider requester = mock(CurrentUserProvider.class);
	final AiRecommendationService engine = new AiRecommendationServiceImpl(requester, repository, new AiRecommendationScoringServiceImpl(weights()));

	@BeforeEach void setup() {
		when(requester.getCurrentUserId()).thenReturn(999L);
		when(repository.developers(anyLong(), anyLong(), anyInt())).thenReturn(List.of());
		when(repository.projects(anyLong(), anyInt())).thenReturn(List.of());
	}
	void developers(List<DeveloperCandidate> data) {
		when(repository.developers(eq(999L), anyLong(), anyInt())).thenAnswer(call -> data.stream()
			.filter(candidate -> candidate.id() > (long) call.getArgument(1)).toList());
	}
	void projects(List<SimilarProjectCandidate> data) {
		when(repository.projects(anyLong(), anyInt())).thenAnswer(call -> data.stream()
			.filter(candidate -> candidate.id() > (long) call.getArgument(0)).toList());
	}

	@Test void emptyCandidatesReturnEmptyListsAndExplanations() {
		var result = engine.recommend(defaults(), "교육");
		assertTrue(result.developers().isEmpty());
		assertTrue(result.projects().isEmpty());
		assertTrue(result.notices().stream().anyMatch(text -> text.contains("후보가 없습니다")));
		assertEquals(0, result.roleCoverage().getFirst().eligibleCount());
	}

	@Test void careerIsRequiredFilterAndRolesAreNotGuessedFromMessages() {
		developers(List.of(
			developer(1, List.of("백엔드 개발자"), List.of("Java"), "3년 차", "36.5"),
			developer(2, List.of("백엔드 개발자"), List.of("Java"), "2년차", "50"),
			developer(3, List.of("프론트엔드 개발자"), List.of("React"), "5년차", "50"),
			developer(4, List.of("백엔드 개발자"), List.of(), null, "50"),
			developer(5, List.of("백엔드 개발자"), List.of(), "약 5년", "50")));
		var result = engine.recommend(defaults(), "프론트엔드 React 5년 이상");
		assertEquals(List.of(1L), result.developers().stream().map(RecommendationResult.Developer::id).toList());
	}

	@Test void anyCareerAllowsNewAndUnknownCareersAndUnselectedRoleAllowsAllJobs() {
		developers(List.of(
			developer(1, List.of("프론트엔드 개발자"), List.of(), "신입", "36.5"),
			developer(2, List.of("백엔드 개발자"), List.of(), null, "36.5"),
			developer(3, List.of("디자이너"), List.of(), "미확인", "36.5"),
			developer(4, List.of(), List.of(), "5년차", "50")));
		var result = engine.recommend(conditions(List.of(), List.of(), RequiredCareer.ANY), null);
		assertEquals(List.of(1L, 2L, 3L), result.developers().stream().map(RecommendationResult.Developer::id).toList());
		assertTrue(result.roleCoverage().isEmpty());
	}

	@Test void candidatesHaveStableScoreOrderIdTiesAndTenResultsIndependentOfRequestedPeople() {
		developers(LongStream.rangeClosed(1, 12).mapToObj(id -> developer(id, List.of("백엔드 개발자"), List.of("Java"), "3년차", "36.5")).toList());
		projects(LongStream.rangeClosed(1, 12).mapToObj(id -> project(id, ProjectStatus.RECRUITING)).toList());
		var first = engine.recommend(defaults(), "교육 플랫폼");
		assertEquals(10, first.developers().size());
		assertEquals(10, first.projects().size());
		assertEquals(LongStream.rangeClosed(1, 10).boxed().toList(), first.developers().stream().map(RecommendationResult.Developer::id).toList());
		assertEquals(LongStream.rangeClosed(1, 10).boxed().toList(), first.projects().stream().map(RecommendationResult.SimilarProject::id).toList());
		assertEquals(1, first.roleCoverage().getFirst().requestedCount());
		assertEquals(12, first.roleCoverage().getFirst().eligibleCount());
		assertEquals(10, first.roleCoverage().getFirst().returnedCount());
		assertEquals(first, engine.recommend(defaults(), "교육 플랫폼"));
	}

	@Test void laterQueryBatchesCanDisplaceEarlierTopCandidates() {
		when(repository.developers(eq(999L), eq(0L), anyInt())).thenReturn(List.of(
			developer(1, List.of("백엔드 개발자"), List.of(), "3년차", "36.5")));
		when(repository.developers(eq(999L), eq(1L), anyInt())).thenReturn(List.of(
			developer(2, List.of("백엔드 개발자"), List.of("Java", "React"), "3년차", "50")));
		assertEquals(List.of(2L, 1L), engine.recommend(defaults(), null).developers().stream().map(RecommendationResult.Developer::id).toList());
	}

	@Test void monthEndAndDifferentOrInvalidDatesDoNotFilterProjectsOrChangeScores() {
		projects(List.of(project(1, ProjectStatus.RECRUITING),
			new SimilarProjectCandidate(2, "교육 플랫폼", "교육 플랫폼", List.of("Java", "React"), List.of("교육"),
				ProjectStatus.RECRUITING, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 3)),
			new SimilarProjectCandidate(3, "교육 플랫폼", "교육 플랫폼", List.of("Java", "React"), List.of("교육"),
				ProjectStatus.RECRUITING, null, null),
			new SimilarProjectCandidate(4, "교육 플랫폼", "교육 플랫폼", List.of("Java", "React"), List.of("교육"),
				ProjectStatus.RECRUITING, LocalDate.of(2026, 2, 28), LocalDate.of(2026, 1, 31))));
		var result = engine.recommend(defaults(), "교육 플랫폼");
		assertEquals(4, result.projects().size());
		assertEquals(0L, result.projects().get(0).duration().differenceDays());
		assertEquals(LocalDate.of(2026, 2, 28), result.projects().get(0).duration().requestedEnd());
		assertEquals(3L, result.projects().get(1).duration().differenceDays());
		assertNull(result.projects().get(2).duration().differenceDays());
		assertNull(result.projects().get(3).duration().differenceDays());
		assertTrue(result.projects().stream().allMatch(project -> project.score().total().equals(n("100.00"))));
	}

	@Test void unauthenticatedExecutionNeverQueriesCandidates() {
		when(requester.getCurrentUserId()).thenThrow(new InsufficientAuthenticationException("로그인 필요"));
		assertThrows(InsufficientAuthenticationException.class, () -> engine.recommend(defaults(), null));
		verifyNoInteractions(repository);
	}
	@Test void semanticEvaluationIncludesCandidatesOutsideOriginalTopTen() throws Exception {
		var mapper = new tools.jackson.databind.ObjectMapper();
		var client = mock(com.devmozip.backend.client.GeminiAnalysisClient.class);
		var evaluator = new com.devmozip.backend.service.AiRequirementEvaluationService(client, mapper);
		var semantic = new AiRecommendationServiceImpl(requester, repository, new AiRecommendationScoringServiceImpl(weights()), evaluator);
		projects(LongStream.rangeClosed(1, 13).mapToObj(id -> new SimilarProjectCandidate(id, "교육 플랫폼",
			id == 13 ? "로그인 구현" : "공개 설명", List.of("Java", "React"), List.of("교육"), ProjectStatus.RECRUITING, null, null)).toList());
		var seen = new java.util.HashSet<String>();
		when(client.generate(anyString(), anyString(), any())).thenAnswer(call -> {
			var input = mapper.readTree((String) call.getArgument(0));
			var rows = mapper.createArrayNode();
			for (var c : input.path("candidates")) {
				String ref = c.path("ref").asText(); seen.add(ref);
				if (ref.equals("project:13")) rows.add(mapper.valueToTree(java.util.Map.of("candidate", ref, "requirement", "r0",
					"status", "MET", "source", ref + ":description", "quote", "로그인 구현")));
			}
			return mapper.createObjectNode().set("evaluations", rows);
		});
		var result = semantic.recommend(defaults(), null, List.of(new com.devmozip.backend.service.AiRequirementSnapshot.Requirement(
			"r0", "FEATURE", "로그인 구현", "로그인 구현", 1)));
		assertEquals(13, seen.size());
		assertEquals(13L, result.projects().getFirst().id());
		assertEquals(10, result.projects().size());
	}
	@Test void publicCandidateEvaluationNeverSendsIdentityOrPremiumDomainData() throws Exception {
		var mapper = new tools.jackson.databind.ObjectMapper();
		var client = mock(com.devmozip.backend.client.GeminiAnalysisClient.class);
		var evaluator = new com.devmozip.backend.service.AiRequirementEvaluationService(client, mapper);
		developers(List.of(new DeveloperCandidate(1, "candidate-1", "tag-1", List.of("백엔드 개발자"), List.of("Java"), List.of("교육"),
			"3년 차", n("50"), "결제 구현 경험이 있습니다")));
		when(client.generate(anyString(), anyString(), any())).thenAnswer(call -> {
			String payload = call.getArgument(0);
			assertFalse(payload.contains("candidate-1")); assertFalse(payload.contains("tag-1"));
			var fields = mapper.readTree(payload).path("candidates").get(0).path("sources");
			assertEquals(java.util.Set.of("skills", "jobCategories", "career", "trustScore", "summary"),
				java.util.stream.StreamSupport.stream(fields.spliterator(), false).map(n -> n.path("field").asText()).collect(java.util.stream.Collectors.toSet()));
			return mapper.valueToTree(java.util.Map.of("evaluations", List.of(java.util.Map.of("candidate", "developer:1",
				"requirement", "r0", "status", "MET", "source", "developer:1:summary", "quote", "결제 구현 경험"))));
		});
		var semantic = new AiRecommendationServiceImpl(requester, repository, new AiRecommendationScoringServiceImpl(weights()), evaluator);
		var result = semantic.recommend(defaults(), null, List.of(new com.devmozip.backend.service.AiRequirementSnapshot.Requirement(
			"r0", "TARGET_EXPERIENCE", "결제 경험 필수", "결제 경험 필수", 1)));
		assertEquals(com.devmozip.backend.dto.ai.RequirementEvaluation.Status.MET,
			result.developers().getFirst().score().requirementEvaluation().assessments().getFirst().status());
		assertEquals("MANDATORY_UNENFORCED", result.developers().getFirst().score().requirementEvaluation().assessments().getFirst().mode());
		assertEquals(1, result.developers().size());
	}

}
