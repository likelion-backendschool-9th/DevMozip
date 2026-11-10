package com.devmozip.backend.dto.profile;

import java.time.OffsetDateTime;
import java.util.List;

public record ProfileReviewsResponse(long userId, long count, double average, List<Long> distribution,
		List<Keyword> keywords, List<Item> items, int page, int size, long totalPages) {
	public record Keyword(String name,long count) {}
	public record Item(long id,Long projectId,String projectTitle,long authorUserId,String authorNickname,
		String authorTag,int rating,List<String> keywords,OffsetDateTime submittedAt) {}
}
