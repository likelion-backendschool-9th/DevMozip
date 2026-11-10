package com.devmozip.backend.dto.ai;

import java.math.BigDecimal;
import java.util.List;

/** 기본 추천에서 공개 자료로 검증한 요구사항 평가다. 프리미엄 상세 이유가 아니다. */
public record RequirementEvaluation(int version, boolean applied, BigDecimal baseScore, BigDecimal requirementScore,
	List<Assessment> assessments, String basicReason) {
	public enum Status { MET, PARTIAL, UNKNOWN, MISMATCH }
	public record Assessment(String requirementId, String requirement, String mode, Status status,
		String source, String field, String quote) {}
}
