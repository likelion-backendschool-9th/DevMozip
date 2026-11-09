package com.devmozip.backend.ai.engine;

import com.devmozip.backend.client.GeminiAnalysisClient;
import com.devmozip.backend.dto.ai.internal.RecommendationResult.Score;
import com.devmozip.backend.service.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiRequirementEvaluationTest {
	final ObjectMapper mapper = new ObjectMapper();
	final GeminiAnalysisClient client = mock(GeminiAnalysisClient.class);
	final AiRequirementEvaluationService service = new AiRequirementEvaluationService(client, mapper);
	AiRequirementEvaluationTest() throws Exception {}
	final List<AiRequirementSnapshot.Requirement> requirements = List.of(
		new AiRequirementSnapshot.Requirement("r0", "FEATURE", "로그인 구현", "로그인 구현", 1));
	final Score base = new Score(new BigDecimal("90"), List.of(), List.of("기본 추천"));
	final AiRequirementEvaluationService.Candidate candidate = new AiRequirementEvaluationService.Candidate("project:1",
		List.of(new AiRequirementEvaluationService.Source("project:1:description", "description", "로그인 구현 경험이 있습니다")));
	void output(String candidateRef, String status, String source, String quote) {
		when(client.generate(anyString(), anyString(), any())).thenReturn(mapper.valueToTree(Map.of("evaluations", List.of(Map.of(
			"candidate", candidateRef, "requirement", "r0", "status", status, "source", source, "quote", quote)))));
	}
	@Test void groundedRequirementChangesScoreAndKeepsPublicSourceAndBaseSeparate() {
		output("project:1", "MET", "project:1:description", "로그인 구현");
		var evaluation = service.evaluate(List.of(candidate), requirements, Map.of(candidate.ref(), base)).get(candidate.ref());
		var score = service.combine(base, evaluation);
		assertEquals(new BigDecimal("92.00"), score.total());
		assertEquals(new BigDecimal("90"), evaluation.baseScore());
		assertEquals(new BigDecimal("100.00"), evaluation.requirementScore());
		assertEquals("project:1:description", evaluation.assessments().getFirst().source());
		assertEquals(evaluation.baseScore(), AiStoredEvaluation.decode(AiStoredEvaluation.encode(score)).baseScore());
	}
	@Test void partialFulfillmentUsesApprovedFiftyPoints() {
		output("project:1", "PARTIAL", "project:1:description", "로그인 구현");
		var evaluation = service.evaluate(List.of(candidate), requirements, Map.of(candidate.ref(), base)).get(candidate.ref());
		assertEquals(new BigDecimal("50.00"), evaluation.requirementScore());
		assertEquals(new BigDecimal("82.00"), service.combine(base, evaluation).total());
	}
	@Test void forgedQuoteOrSourceCannotCreateExperienceAndUnknownStillCountsAsZero() {
		for (var evidence : List.of(List.of("project:1:description", "없는 결제 경험"), List.of("private:99", "로그인 구현"))) {
			output("project:1", "MET", evidence.get(0), evidence.get(1));
			var evaluation = service.evaluate(List.of(candidate), requirements, Map.of(candidate.ref(), base)).get(candidate.ref());
			assertEquals(com.devmozip.backend.dto.ai.RequirementEvaluation.Status.UNKNOWN, evaluation.assessments().getFirst().status());
			assertNull(evaluation.assessments().getFirst().quote());
			assertEquals(new BigDecimal("72.00"), service.combine(base, evaluation).total());
		}
	}
	@Test void missingEvidenceDiffersFromExplicitMismatchAndMissingPairsAreUnknown() {
		output("project:1", "MISMATCH", "project:1:description", "없는 내용");
		assertEquals(com.devmozip.backend.dto.ai.RequirementEvaluation.Status.UNKNOWN,
			service.evaluate(List.of(candidate), requirements, Map.of(candidate.ref(), base)).get(candidate.ref()).assessments().getFirst().status());
		var contrary = new AiRequirementEvaluationService.Candidate("project:1", List.of(
			new AiRequirementEvaluationService.Source("project:1:description", "description", "로그인 구현은 하지 않았습니다")));
		output("project:1", "MISMATCH", "project:1:description", "하지 않았습니다");
		assertEquals(com.devmozip.backend.dto.ai.RequirementEvaluation.Status.MISMATCH,
			service.evaluate(List.of(contrary), requirements, Map.of(contrary.ref(), base)).get(contrary.ref()).assessments().getFirst().status());
	}
	@Test void technologyListCannotProveImplementationExperienceEvenWithARealQuote() {
		var developer = new AiRequirementEvaluationService.Candidate("project:1", List.of(
			new AiRequirementEvaluationService.Source("project:1:description", "skills", "Java")));
		output("project:1", "MET", "project:1:description", "Java");
		var required = List.of(new AiRequirementSnapshot.Requirement("r0", "TARGET_EXPERIENCE", "결제 구현 경험", "결제 경험", 1));
		assertEquals(com.devmozip.backend.dto.ai.RequirementEvaluation.Status.UNKNOWN,
			service.evaluate(List.of(developer), required, Map.of(developer.ref(), base)).get(developer.ref()).assessments().getFirst().status());
	}
	@Test void unrestrictedAndExplicitlyWaivedRequirementsRemainContextWithoutScoring() {
		var snapshots = new AiRequirementSnapshot(mapper, new AiConditionsDocument(mapper));
		String current = mapper.writeValueAsString(Map.of("_analysis", Map.of("facts", List.of(
			Map.of("kind", "PROJECT_PREFERENCE", "value", "주제 제한 없음", "origin", "EXPLICIT"),
			Map.of("kind", "TARGET_EXPERIENCE", "value", "결제 경험 없어도 됨", "origin", "EXPLICIT")))));
		String snapshot = snapshots.capture(current, "{}", null);
		assertTrue(snapshots.requirements(snapshot).isEmpty());
		assertEquals(2, mapper.readTree(snapshot).path("_requirements").path("facts").size());
	}
	@Test void inventedCandidateAndProviderFailureDoNotFallBackToBasicRecommendation() {
		output("project:99", "MET", "project:1:description", "로그인 구현");
		assertThrows(RuntimeException.class, () -> service.evaluate(List.of(candidate), requirements, Map.of(candidate.ref(), base)));
		when(client.generate(anyString(), anyString(), any())).thenThrow(new IllegalStateException("provider unavailable"));
		assertThrows(RuntimeException.class, () -> service.evaluate(List.of(candidate), requirements, Map.of(candidate.ref(), base)));
	}
	@Test void snapshotSeparatesOwnSkillsInferencesPurposeAndTargetAndRemainsImmutable() {
		var documents = new AiConditionsDocument(mapper);
		var snapshots = new AiRequirementSnapshot(mapper, documents);
		String current = mapper.writeValueAsString(Map.of("_analysis", Map.of("facts", List.of(
			Map.of("kind", "OWN_SKILL", "value", "Java", "origin", "EXPLICIT"),
			Map.of("kind", "PURPOSE", "value", "프로젝트 참여", "origin", "EXPLICIT"),
			Map.of("kind", "TARGET_SKILL", "value", "Python", "origin", "INFERRED"),
			Map.of("kind", "TARGET_EXPERIENCE", "value", "결제 경험 필수", "origin", "EXPLICIT")))));
		String snapshot = snapshots.capture(current, "{\"interests\":[\"웹\"],\"career\":\"경력 무관\",\"durationMonths\":1}", null);
		assertEquals(1, snapshots.requirements(snapshot).size());
		assertEquals("결제 경험 필수", snapshots.requirements(snapshot).getFirst().value());
		assertEquals(4, mapper.readTree(snapshot).path("_requirements").path("facts").size());
		assertFalse(snapshots.same(snapshot, snapshots.capture("{}", "{}", null)));
		assertEquals(1, documents.recommendation(snapshot).durationMonths());
		assertTrue(service.evaluate(List.of(candidate), List.of(), Map.of(candidate.ref(), base)).isEmpty());
		verifyNoInteractions(client);
	}
}
