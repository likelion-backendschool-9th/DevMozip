package com.devmozip.backend.dto.search;

import java.util.List;
import org.springframework.util.MultiValueMap;

/**
 * 개발자 검색 조건(WBS 6.2·6.5). 직군·경력·관심 분야·기술 스택은 저장된 문구 그대로 비교하므로
 * 목록에 없는 문구는 거절하지 않고 결과가 없게 된다.
 *
 * @param keyword 앞뒤 공백을 지운 검색어. 없으면 null
 * @param available null이면 협업 상태로 거르지 않는다
 * @param following true면 요청자가 팔로우한 회원만
 */
public record DeveloperSearchQuery(
	String keyword,
	String jobCategory,
	String career,
	List<String> interests,
	List<String> skills,
	Boolean available,
	boolean following,
	int page,
	int size
) {
	public static DeveloperSearchQuery from(MultiValueMap<String, String> params) {
		SearchParameters p = new SearchParameters(params);
		return new DeveloperSearchQuery(
			p.text("q", SearchParameters.MAX_QUERY_LENGTH, "검색어는 100자 이내로 입력해 주세요."),
			p.text("jobCategory", SearchParameters.MAX_VALUE_LENGTH, "개발 직군을 확인해 주세요."),
			p.text("career", SearchParameters.MAX_VALUE_LENGTH, "경력을 확인해 주세요."),
			p.values("interests", "관심 분야는 30자 이내로 30개까지 선택해 주세요."),
			p.values("skills", "기술 스택은 30자 이내로 30개까지 선택해 주세요."),
			p.bool("available", "협업 가능 여부는 true 또는 false로 보내 주세요."),
			Boolean.TRUE.equals(p.bool("following", "팔로잉 여부는 true 또는 false로 보내 주세요.")),
			p.page(),
			p.size());
	}
}
