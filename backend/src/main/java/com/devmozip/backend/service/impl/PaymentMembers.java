package com.devmozip.backend.service.impl;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberStatus;
import com.devmozip.backend.repository.MemberRepository;

/** 구독·결제 요청자 확인. 인증 정보의 이메일로만 회원을 찾고 정지 회원은 막는다 */
final class PaymentMembers {
	private PaymentMembers() {
	}

	static Member load(MemberRepository members, String email) {
		return check(members.findByEmail(email).orElseThrow(() -> new InsufficientAuthenticationException("로그인 회원을 찾을 수 없습니다.")));
	}

	/** 구독 상태를 확인한 뒤 바꾸는 작업이 겹치지 않도록 회원 행을 잠근다 */
	static Member lock(MemberRepository members, String email) {
		return check(members.findForAccountUpdateByEmail(email).orElseThrow(() -> new InsufficientAuthenticationException("로그인 회원을 찾을 수 없습니다.")));
	}

	private static Member check(Member member) {
		if (member.getStatus() == MemberStatus.SUSPENDED) throw new LockedException("정지된 계정입니다.");
		return member;
	}
}
