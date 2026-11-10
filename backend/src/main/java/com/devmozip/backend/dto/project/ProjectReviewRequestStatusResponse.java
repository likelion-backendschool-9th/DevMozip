package com.devmozip.backend.dto.project;

import java.time.LocalDateTime;

public record ProjectReviewRequestStatusResponse(
	long userId, String nickname, int total, int submitted, LocalDateTime expiresAt
) {}
