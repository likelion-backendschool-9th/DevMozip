package com.devmozip.backend.dto.admin;

import com.devmozip.backend.entity.ReviewKeyword;

/** 리뷰 키워드 하나 (WBS 14.9). active가 false면 새 리뷰의 선택지에서 빠진 키워드다. */
public record AdminReviewKeywordResponse(long id, String name, boolean active) {
	public static AdminReviewKeywordResponse of(ReviewKeyword keyword) {
		return new AdminReviewKeywordResponse(keyword.getId(), keyword.getName(), keyword.isActive());
	}
}
