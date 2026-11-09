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
}
