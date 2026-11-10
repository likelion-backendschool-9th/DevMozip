package com.devmozip.backend.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** 통합 검색어 정리. NFKC 정규화와 소문자 변환 뒤 공백으로 나눈다(목업 searchService의 matches와 같다). */
public final class SearchKeywords {
	private SearchKeywords() {}

	/** 중복을 지운 검색 단어. 검색어가 없으면 빈 목록이다. */
	public static List<String> tokens(String keyword) {
		if (keyword == null) return List.of();
		return Arrays.stream(normalize(keyword).split("\\s+"))
			.filter(token -> !token.isEmpty())
			.distinct()
			.toList();
	}

	/** 7.1 목록(ProjectQueryRepository)과 같은 정리. 필터 값을 DB의 lower()와 비교할 때 쓴다. */
	public static String normalize(String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
	}

	/** LIKE의 이스케이프 문자는 '!'다. */
	public static String containsPattern(String token) {
		return "%" + token.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
	}
}
