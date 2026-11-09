package com.devmozip.backend.service;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 경로의 {slug}(users.tag 원문)로 팔로우 대상을 찾는다. 프로필 조회(ProfileService.findBySlug)와 같은 조건이다.
 * ProfileService는 프로필 담당 코드이므로 고치지 않고 같은 조건을 여기에 둔다. 목록과 수에는 이 조건을 쓰지 않는다.
 */
@Component
public class FollowTargetFinder {
	private final MemberRepository members;

	public FollowTargetFinder(MemberRepository members) {
		this.members = members;
	}

	public Member find(String slug) {
		Member member = members.findByTagAndStatus(slug, Member.MemberStatus.ACTIVE).orElseThrow(FollowTargetFinder::notFound);
		if (member.getJobCategories() == null || member.getJobCategories().isEmpty()) throw notFound();
		return member;
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
	}
}
