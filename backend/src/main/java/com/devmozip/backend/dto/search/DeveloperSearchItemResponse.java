package com.devmozip.backend.dto.search;

import java.math.BigDecimal;
import java.util.List;
import com.devmozip.backend.entity.Member;

/**
 * 개발자 카드 한 장(WBS 6.4). 이메일·회원 id·연락 가능 시간·결제 정보는 넣지 않는다.
 * followed는 요청자가 이 회원을 팔로우하는지다(비로그인이면 false).
 */
public record DeveloperSearchItemResponse(
	String tag,
	String nickname,
	String profileImage,
	List<String> jobCategories,
	String career,
	String summary,
	List<String> skills,
	List<String> interests,
	String collaborationStatus,
	BigDecimal trustScore,
	boolean followed
) {
	public static DeveloperSearchItemResponse from(Member member, boolean followed) {
		return new DeveloperSearchItemResponse(
			member.getTag(),
			member.getNickname(),
			member.getProfileImage(),
			copy(member.getJobCategories()),
			member.getCareer(),
			member.getSummary(),
			copy(member.getSkills()),
			copy(member.getInterests()),
			member.getCollaborationStatus() == null ? null : member.getCollaborationStatus().name(),
			member.getTrustScore(),
			followed);
	}

	private static List<String> copy(List<String> values) {
		return values == null ? List.of() : List.copyOf(values);
	}
}
