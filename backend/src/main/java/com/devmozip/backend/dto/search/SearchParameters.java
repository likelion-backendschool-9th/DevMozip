package com.devmozip.backend.dto.search;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

/**
 * 검색 쿼리 읽기. 스프링 바인딩은 값 하나에 든 쉼표를 목록으로 나누므로(예: 직접 입력한 기술 스택) 쿼리를 그대로 읽는다.
 * 하나만 받는 값이 여러 번 오거나 형식이 틀리면 400으로 응답한다.
 */
final class SearchParameters {
	static final int MAX_QUERY_LENGTH = 100;
	static final int MAX_VALUE_LENGTH = 30;
	static final int MAX_VALUES = 30;
	static final int DEFAULT_SIZE = 20;
	static final int MAX_SIZE = 100;

	private final MultiValueMap<String, String> params;

	SearchParameters(MultiValueMap<String, String> params) {
		this.params = params;
	}

	/** 앞뒤 공백을 지운 값. 없거나 비어 있으면 null이다. */
	String text(String key, int maxLength, String message) {
		String value = single(key);
		if (value == null) return null;
		if (value.length() > maxLength) throw invalid(message);
		String trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/** 반복 값. 공백만 있는 값은 빼고 중복을 지운다. */
	List<String> values(String key, String message) {
		List<String> raw = params.get(key);
		if (raw == null) return List.of();
		Set<String> values = new LinkedHashSet<>();
		for (String value : raw) {
			if (value == null) continue;
			String trimmed = value.strip();
			if (trimmed.isEmpty()) continue;
			if (trimmed.length() > MAX_VALUE_LENGTH) throw invalid(message);
			values.add(trimmed);
		}
		if (values.size() > MAX_VALUES) throw invalid(message);
		return List.copyOf(values);
	}

	Boolean bool(String key, String message) {
		String value = single(key);
		if (value == null || value.isEmpty()) return null;
		if (value.equals("true")) return true;
		if (value.equals("false")) return false;
		throw invalid(message);
	}

	int page() {
		return number("page", 0, 0, Integer.MAX_VALUE);
	}

	int size() {
		return number("size", DEFAULT_SIZE, 1, MAX_SIZE);
	}

	String single(String key) {
		List<String> values = params.get(key);
		if (values == null || values.isEmpty()) return null;
		if (values.size() > 1) throw invalid("검색 조건을 확인해 주세요.");
		return values.getFirst();
	}

	private int number(String key, int fallback, int min, int max) {
		String value = single(key);
		if (value == null || value.isEmpty()) return fallback;
		if (!value.matches("\\d{1,9}")) throw invalid("페이지와 조회 개수를 확인해 주세요.");
		int number = Integer.parseInt(value);
		if (number < min || number > max) throw invalid("페이지와 조회 개수를 확인해 주세요.");
		return number;
	}

	static ResponseStatusException invalid(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
