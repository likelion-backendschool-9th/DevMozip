package com.devmozip.backend.dto.admin;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * 관리자 목록의 공통 응답 (docs/admin-plan.md 2.3). 기존 프로젝트 신고 관리 응답과 같은 필드 이름을 쓴다.
 * page는 0부터 센다.
 */
public record AdminPageResponse<T>(List<T> items, long totalElements, int page, int totalPages) {
	public static <S, T> AdminPageResponse<T> of(Page<S> rows, Function<S, T> mapper) {
		return new AdminPageResponse<>(rows.getContent().stream().map(mapper).toList(),
			rows.getTotalElements(), rows.getNumber(), rows.getTotalPages());
	}

	public static <T> AdminPageResponse<T> of(List<T> items, long totalElements, int page, int size) {
		int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
		return new AdminPageResponse<>(List.copyOf(items), totalElements, page, totalPages);
	}
}
