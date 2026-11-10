package com.devmozip.backend.dto.admin;

import com.devmozip.backend.entity.Member;

/** 관리자 화면의 회원 표시 (신고자·신고 대상). 탈퇴 회원(#135)은 tag가 null이고 withdrawn이 true다. */
public record AdminPartyResponse(long id, String tag, String nickname, boolean withdrawn) {
	public static AdminPartyResponse of(Member member) {
		if (member == null) return null;
		boolean withdrawn = member.getEmail() == null && member.getStatus() == null;
		return new AdminPartyResponse(member.getId(), AdminPostResponse.tagOf(member), member.getNickname(), withdrawn);
	}
}
