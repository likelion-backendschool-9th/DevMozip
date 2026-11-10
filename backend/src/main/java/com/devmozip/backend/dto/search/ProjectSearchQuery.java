package com.devmozip.backend.dto.search;

import java.util.List;
import org.springframework.util.MultiValueMap;

/**
 * 프로젝트 탐색 조건(WBS 6.3). 7.1 목록 요청(dto/project/ProjectSearchRequest)과 따로 둔다.
 *
 * @param keyword 앞뒤 공백을 지운 검색어. 없으면 null
 * @param jobRole 모집 직군 코드 또는 직접 입력한 직군. 없으면 null
 */
public record ProjectSearchQuery(
	String keyword,
	String jobRole,
	ProjectSearchStatus status,
	List<String> interests,
	List<String> techStacks,
	int page,
	int size
) {
	public static ProjectSearchQuery from(MultiValueMap<String, String> params) {
		SearchParameters p = new SearchParameters(params);
		return new ProjectSearchQuery(
			p.text("q", SearchParameters.MAX_QUERY_LENGTH, "검색어는 100자 이내로 입력해 주세요."),
			p.text("jobRole", SearchParameters.MAX_VALUE_LENGTH, "모집 직군을 확인해 주세요."),
			ProjectSearchStatus.parse(p.single("status")),
			p.values("interests", "프로젝트 분야는 30자 이내로 30개까지 선택해 주세요."),
			p.values("techStacks", "기술 스택은 30자 이내로 30개까지 선택해 주세요."),
			p.page(),
			p.size());
	}
}
