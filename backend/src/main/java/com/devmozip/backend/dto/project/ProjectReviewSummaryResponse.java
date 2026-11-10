package com.devmozip.backend.dto.project;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectReviewSummaryResponse(
	long userId, long count, double average, List<Long> distribution,
	List<Keyword> keywords, List<Received> reviews
) {
	public record Keyword(String name, long count) {}
	public record Received(long id, long projectId, String projectTitle, long authorUserId,
		String authorNickname, String authorTag, int rating, List<String> keywords,
		LocalDateTime submittedAt) {}
}
