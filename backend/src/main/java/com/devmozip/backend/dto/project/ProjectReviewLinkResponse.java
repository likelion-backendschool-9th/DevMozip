package com.devmozip.backend.dto.project;

import java.time.LocalDateTime;

public record ProjectReviewLinkResponse(String href, LocalDateTime expiresAt) {
	@Override
	public String toString() {
		return "ProjectReviewLinkResponse[REDACTED]";
	}
}
