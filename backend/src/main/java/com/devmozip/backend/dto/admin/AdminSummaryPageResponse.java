package com.devmozip.backend.dto.admin;

import java.util.List;

/** 요약 값이 있는 관리자 목록 (AI 사용량·구독·결제). 목록 필드는 AdminPageResponse와 같다. */
public record AdminSummaryPageResponse<S, T>(S summary, List<T> items, long totalElements, int page, int totalPages) {
	public static <S, T> AdminSummaryPageResponse<S, T> of(S summary, AdminPageResponse<T> page) {
		return new AdminSummaryPageResponse<>(summary, page.items(), page.totalElements(), page.page(), page.totalPages());
	}
}
