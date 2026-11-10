package com.devmozip.backend.dto.admin;

import java.util.List;
import com.devmozip.backend.entity.ReviewKeyword;

/** 리뷰 키워드 목록 (WBS 14.9). 비활성 키워드도 포함하며 ID 오름차순이다. */
public record AdminReviewKeywordsResponse(List<AdminReviewKeywordResponse> items) {
	public static AdminReviewKeywordsResponse of(List<ReviewKeyword> keywords) {
		return new AdminReviewKeywordsResponse(keywords.stream().map(AdminReviewKeywordResponse::of).toList());
	}
}
