package com.devmozip.backend.security.ai;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

final class AuthenticatedCurrentUserProvider implements CurrentUserProvider {
	private final MemberRepository members;

	AuthenticatedCurrentUserProvider(MemberRepository members) {
		this.members = members;
	}

	@Override
	public long getCurrentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
			|| authentication instanceof AnonymousAuthenticationToken
			|| authentication.getName() == null || authentication.getName().isBlank()) {
			throw new InsufficientAuthenticationException("로그인이 필요합니다.");
		}
		// The existing JWT filter supplies the verified email, never a client-supplied user ID.
		Member member = members.findByEmail(authentication.getName())
			.orElseThrow(() -> new InsufficientAuthenticationException("로그인 사용자를 찾을 수 없습니다."));
		if (member.getStatus() == Member.MemberStatus.SUSPENDED) {
			throw new LockedException("정지된 계정입니다.");
		}
		return member.getId();
	}
}
