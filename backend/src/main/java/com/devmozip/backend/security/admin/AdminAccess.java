package com.devmozip.backend.security.admin;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.security.member.CurrentMemberProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관리자 API의 요청자 확인 (docs/admin-plan.md 4.2).
 * SecurityConfig가 JWT의 ADMIN 역할을 먼저 확인하고, 여기서 DB의 회원이 지금도 ADMIN·ACTIVE인지 다시 확인한다.
 * 토큰을 발급한 뒤 DB 역할·상태가 바뀐 경우를 막는다. 모든 관리자 서비스는 처음에 require()를 부른다.
 */
@Component
public class AdminAccess {
	private final CurrentMemberProvider currentMember;

	public AdminAccess(CurrentMemberProvider currentMember) {
		this.currentMember = currentMember;
	}

	/** 요청자가 DB에서 ADMIN이며 ACTIVE인 회원이면 그 회원을 돌려준다. 로그인하지 않았으면 401, 그 밖에는 403이다. */
	public Member require() {
		Member member = currentMember.find()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
		if (member.getRole() != Member.MemberRole.ADMIN || member.getStatus() != Member.MemberStatus.ACTIVE) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 이용할 수 있습니다.");
		}
		return member;
	}
}
