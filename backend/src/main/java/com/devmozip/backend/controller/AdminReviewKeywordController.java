package com.devmozip.backend.controller;

import com.devmozip.backend.dto.admin.AdminReviewKeywordsRequest;
import com.devmozip.backend.dto.admin.AdminReviewKeywordsResponse;
import com.devmozip.backend.service.AdminReviewKeywordService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** WBS 14.9 리뷰 키워드 관리. GET은 목록 로드, PUT은 활성 키워드 목록 수정이다. */
@RestController
@RequestMapping("/api/v1/admin/review-keywords")
public class AdminReviewKeywordController {
	private final AdminReviewKeywordService keywords;

	public AdminReviewKeywordController(AdminReviewKeywordService keywords) {
		this.keywords = keywords;
	}

	@GetMapping
	public AdminReviewKeywordsResponse list() {
		return keywords.list();
	}

	@PutMapping
	public AdminReviewKeywordsResponse replace(@Valid @RequestBody AdminReviewKeywordsRequest request) {
		return keywords.replace(request);
	}
}
