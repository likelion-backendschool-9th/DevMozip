package com.devmozip.backend.dto.follow;

import java.util.List;
import com.devmozip.backend.entity.Member;

/**
 * 팔로워·팔로잉 목록의 한 사람. 이메일·사용자 id·결제 정보는 넣지 않는다.
 * followed·activityNotificationEnabled는 요청자가 이 사람을 팔로우하는지와 그 알림 설정이다(비로그인이면 false).
 */
public record FollowUserResponse(
	String tag,
	String nickname,
	String profileImage,
	List<String> jobCategories,
	String career,
	List<String> skills,
	String collaborationStatus,
	boolean followed,
	boolean activityNotificationEnabled
) {
	public static FollowUserResponse from(Member member, boolean followed, boolean activityNotificationEnabled) {
		return new FollowUserResponse(
			member.getTag(),
			member.getNickname(),
			member.getProfileImage(),
			member.getJobCategories() == null ? List.of() : List.copyOf(member.getJobCategories()),
			member.getCareer(),
			member.getSkills() == null ? List.of() : List.copyOf(member.getSkills()),
			member.getCollaborationStatus() == null ? null : member.getCollaborationStatus().name(),
			followed,
			activityNotificationEnabled
		);
	}
}
