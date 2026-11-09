package com.devmozip.backend.dto.ai;

import java.time.LocalDateTime;
import java.util.List;

/** 프리미엄 전용 응답. 일반 추천 DTO에는 포함하지 않는다. */
public record PortfolioAnalysisResponse(long recommendationId, String status, boolean cached,
	LocalDateTime generatedAt, List<Portfolio> portfolios, List<Assessment> assessments,
	List<String> questions, String notice) {
	public record Portfolio(long id, String title, String selectionReason, boolean truncated) {}
	public record Assessment(String requirementId, String requirement, String status, Long portfolioId,
		String field, String quote) {}
}
