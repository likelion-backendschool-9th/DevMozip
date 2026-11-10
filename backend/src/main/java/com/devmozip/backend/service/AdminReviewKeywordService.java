package com.devmozip.backend.service;

import com.devmozip.backend.dto.admin.AdminReviewKeywordsRequest;
import com.devmozip.backend.dto.admin.AdminReviewKeywordsResponse;

/** WBS 14.9 리뷰 키워드 관리 (docs/admin-plan.md 5.8). 삭제는 비활성이며 행과 기존 리뷰 연결은 남긴다. 이름 변경은 없다. */
public interface AdminReviewKeywordService {
	AdminReviewKeywordsResponse list();

	/** 목록에 있는 이름은 활성(없으면 만든다), 목록에 없는 활성 키워드는 비활성으로 바꾼다. */
	AdminReviewKeywordsResponse replace(AdminReviewKeywordsRequest request);
}
