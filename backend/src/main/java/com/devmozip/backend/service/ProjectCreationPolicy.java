package com.devmozip.backend.service;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 구독 해지 후 기존 공고 관리는 제한하지 않고 신규 생성에만 적용한다. */
@Component
public class ProjectCreationPolicy {
	private final MemberRepository members;
	public ProjectCreationPolicy(MemberRepository members) { this.members=members; }
	public void requirePermission(long userId) {
		Member member=members.findActiveForAccountUpdateById(userId).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"로그인 회원을 찾을 수 없습니다."));
		if ((member.getStatus()!=Member.MemberStatus.ACTIVE && member.getStatus()!=Member.MemberStatus.WARNED)
			|| member.getRole()!=Member.MemberRole.PREMIUM)
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,"프리미엄 회원만 모집 공고를 생성할 수 있습니다.");
	}
}
