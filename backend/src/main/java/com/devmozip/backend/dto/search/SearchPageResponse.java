package com.devmozip.backend.dto.search;

import java.util.List;

/**
 * 검색 결과 한 페이지(WBS 6.10). totalCount는 검색 조건을 적용한 전체 수다.
 * 전체 페이지보다 큰 page를 요청하면 빈 items와 실제 totalCount·totalPages를 돌려준다.
 */
public record SearchPageResponse<T>(List<T> items, int page, int size, long totalCount, int totalPages) {
	public static <T> SearchPageResponse<T> of(List<T> items, int page, int size, long totalCount) {
		return new SearchPageResponse<>(items, page, size, totalCount, (int) ((totalCount + size - 1) / size));
	}
}
