package com.devmozip.backend.dto.feed;

import java.util.List;

import com.devmozip.backend.entity.Member;

/** 게시글·댓글 작성자. 화면은 tag로 프로필 주소(/users/{tag})를 만든다. tag가 없으면 프로필 링크를 두지 않는다. */
public record FeedAuthorResponse(long id, String nickname, String tag, String profileImage, List<String> jobCategories) {
	public static FeedAuthorResponse from(Member member) {
		return new FeedAuthorResponse(member.getId(), member.getNickname(), member.getTag(), member.getProfileImage(),
			member.getJobCategories() == null ? List.of() : List.copyOf(member.getJobCategories()));
	}
}
