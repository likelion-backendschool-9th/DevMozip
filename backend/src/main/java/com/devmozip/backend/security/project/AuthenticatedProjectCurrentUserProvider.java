package com.devmozip.backend.security.project;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 기존 JWT 필터가 검증한 이메일로만 실제 프로젝트 요청자를 식별한다. */
final class AuthenticatedProjectCurrentUserProvider implements ProjectCurrentUserProvider {
	private final MemberRepository members;
	AuthenticatedProjectCurrentUserProvider(MemberRepository members) { this.members = members; }
	@Override
	public long getCurrentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
			|| authentication instanceof AnonymousAuthenticationToken
			|| authentication.getName() == null || authentication.getName().isBlank())
			throw new InsufficientAuthenticationException("로그인이 필요합니다.");
		Member member = members.findByEmail(authentication.getName())
			.orElseThrow(() -> new InsufficientAuthenticationException("로그인 회원을 찾을 수 없습니다."));
		if (member.getStatus() != Member.MemberStatus.ACTIVE && member.getStatus() != Member.MemberStatus.WARNED)
			throw new LockedException("이 계정은 프로젝트를 이용할 수 없습니다.");
		if (member.getId() == null || member.getId() <= 0)
			throw new InsufficientAuthenticationException("회원 ID를 확인해 주세요.");
		return member.getId();
	}
}
