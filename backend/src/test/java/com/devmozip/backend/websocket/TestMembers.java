package com.devmozip.backend.websocket;

import java.util.List;
import java.util.UUID;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;

final class TestMembers {
	private TestMembers() {
	}

	static Member create(MemberRepository members) {
		String name = "ws" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		return members.save(Member.builder()
			.email(name + "@devmozip.test")
			.nickname(name)
			.tag(name)
			.role(Member.MemberRole.USER)
			.status(Member.MemberStatus.ACTIVE)
			.collaborationStatus(Member.CollaborationStatus.AVAILABLE)
			.jobCategories(List.of("백엔드 개발자"))
			.build());
	}
}
