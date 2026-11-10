package com.devmozip.backend.security.member;

import java.util.Optional;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 요청자 식별. JWT 필터가 검증한 이메일(Authentication.getName())로만 회원을 찾고, 요청 본문·쿼리로 받은 사용자 ID는 쓰지 않는다.
 * 팔로우·SNS 피드·개발자 탐색이 함께 쓴다. 프로젝트(ProjectCurrentUserProvider)와 AI(CurrentUserProvider)는 각 담당 코드이므로 바꾸지 않는다.
 */
@Component
public class CurrentMemberProvider {
	private final MemberRepository members;

	public CurrentMemberProvider(MemberRepository members) {
		this.members = members;
	}

	/** 로그인하지 않았거나 회원을 찾을 수 없으면 비어 있다. 공개 조회에서 요청자 기준 값을 만들 때 쓴다. */
	public Optional<Member> find() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
			|| authentication instanceof AnonymousAuthenticationToken
			|| authentication.getName() == null || authentication.getName().isBlank()) {
			return Optional.empty();
		}
		return members.findByEmail(authentication.getName());
	}

	/** 쓰기 요청의 요청자. 로그인하지 않았으면 401, ACTIVE·WARNED가 아니면 403으로 응답한다. */
	public Member requireActive() {
		Member member = find()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
		if (member.getStatus() != Member.MemberStatus.ACTIVE && member.getStatus() != Member.MemberStatus.WARNED) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 계정 상태에서는 이용할 수 없습니다.");
		}
		return member;
	}
}
