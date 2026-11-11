package com.devmozip.backend.service;

import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관리자 목록의 페이지·검색어 규칙 (docs/admin-plan.md 2.3, 4.4).
 * 페이지 크기 범위는 기존 프로젝트 신고 관리 API와 같다(1~50, 기본 20).
 */
public final class AdminPaging {
	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 50;
	/** LIKE 검색어의 이스케이프 문자. 쿼리에는 escape '\' 로 쓴다. */
	public static final char ESCAPE = '\\';

	private AdminPaging() {
	}

	/** page는 0 이상, size는 1~50이어야 한다. 정렬은 각 쿼리가 정한다. */
	public static PageRequest request(int page, int size) {
		if (page < 0 || size < 1 || size > MAX_SIZE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지를 확인해 주세요.");
		}
		return PageRequest.of(page, size);
	}

	/** 목록 필터의 enum 값. 비어 있으면 null(조건 없음), 모르는 값이면 400과 안내 문구다. */
	public static <E extends Enum<E>> E option(Class<E> type, String value, String message) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Enum.valueOf(type, value);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
		}
	}

	/**
	 * 부분 일치 검색어. 앞뒤 공백을 빼고 소문자로 바꾼 뒤 %·_·\ 를 글자 그대로 찾도록 이스케이프한다.
	 * 비어 있으면 null이다(검색 조건 없음).
	 */
	public static String keyword(String q) {
		if (q == null || q.isBlank()) {
			return null;
		}
		String value = q.strip().toLowerCase(Locale.ROOT);
		StringBuilder escaped = new StringBuilder(value.length() + 2).append('%');
		for (char c : value.toCharArray()) {
			if (c == '%' || c == '_' || c == ESCAPE) {
				escaped.append(ESCAPE);
			}
			escaped.append(c);
		}
		return escaped.append('%').toString();
	}
}
