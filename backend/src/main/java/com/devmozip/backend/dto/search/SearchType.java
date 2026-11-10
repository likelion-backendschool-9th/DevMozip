package com.devmozip.backend.dto.search;

import org.springframework.util.MultiValueMap;

/** GET /api/v1/search의 type. 쿼리 값은 소문자 복수형이다(WBS 6.1). */
public enum SearchType {
	DEVELOPERS("developers"),
	PROJECTS("projects");

	private final String value;

	SearchType(String value) {
		this.value = value;
	}

	/** type이 없거나 다른 값이면 400으로 응답한다. */
	public static SearchType from(MultiValueMap<String, String> params) {
		String raw = new SearchParameters(params).single("type");
		for (SearchType type : values()) {
			if (type.value.equals(raw)) return type;
		}
		throw SearchParameters.invalid("검색 유형(type)은 developers 또는 projects로 보내 주세요.");
	}
}
