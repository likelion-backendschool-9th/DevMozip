package com.devmozip.backend.dto.admin;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Sanction;

/**
 * 관리자 회원 목록 항목 (WBS 14.2). 비밀번호 해시·카카오 ID·결제 수단은 넣지 않는다.
 * tag는 비어 있을 수 있다(가입 때 태그를 정하지 못한 회원). latestSanction은 제재 기록이 없으면 null이다.
 */
public record AdminMemberResponse(long id, String tag, String nickname, String email, String role, String status,
	AdminSanctionResponse latestSanction) {

	public static AdminMemberResponse of(Member member, Sanction latest) {
		String tag = member.getTag() == null || member.getTag().isBlank() ? null : member.getTag();
		return new AdminMemberResponse(member.getId(), tag, member.getNickname(), member.getEmail(),
			member.getRole() == null ? null : member.getRole().name(),
			member.getStatus() == null ? null : member.getStatus().name(), AdminSanctionResponse.of(latest));
	}
}
