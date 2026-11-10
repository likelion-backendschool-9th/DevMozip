package com.devmozip.backend.dto.ai.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 엔진 내부 결과다. 외부 API·저장 계약이나 프리미엄 상세 분석 응답이 아니다. */
public record RecommendationResult(List<Developer> developers, List<SimilarProject> projects,
	List<RoleCoverage> roleCoverage, List<String> notices) {
	public enum Evaluation { SCORED, NOT_REQUESTED, UNASSESSED }
	public record ScoreItem(String name, Evaluation evaluation, BigDecimal weight, BigDecimal points) {}
	public record Score(BigDecimal total, List<ScoreItem> items, List<String> basicReasons,
		com.devmozip.backend.dto.ai.RequirementEvaluation requirementEvaluation) {
		public Score(BigDecimal total, List<ScoreItem> items, List<String> basicReasons) { this(total, items, basicReasons, null); }
	}
	public record Developer(long id, String nickname, String tag, List<String> matchedRoles, Score score) {}
	public record SimilarProject(long id, String title, Score score, DurationComparison duration) {}
	/** 서로 다른 직군의 후보 수는 중복될 수 있다. 실제 팀 배정·충원 완료를 뜻하지 않는다. */
	public record RoleCoverage(String role, int requestedCount, long eligibleCount, long returnedCount) {}
	/** 기간 필터·가산점은 적용하지 않고 원래 날짜와 조건 종료일을 비교한다. */
	public record DurationComparison(LocalDate start, LocalDate end, LocalDate requestedEnd, Long differenceDays) {}
}
