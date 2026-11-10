package com.devmozip.backend.dto.project;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectReviewResponse(
	long projectId, String title, LocalDateTime completedAt, LocalDateTime expiresAt,
	boolean submitted, boolean expired, List<String> keywords, List<Target> targets
) {
	public record Target(long memberId, long userId, String nickname, String tag,
		String status, Integer rating, List<String> keywords) {}
}
