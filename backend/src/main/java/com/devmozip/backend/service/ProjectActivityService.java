package com.devmozip.backend.service;

import com.devmozip.backend.dto.project.ProjectActivityPageResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.ProjectActivityRepository;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ProjectActivityService {
	private final MemberRepository members;
	private final ProjectActivityRepository activities;
	private final ProjectCurrentUserProvider currentUser;
	public ProjectActivityService(MemberRepository members, ProjectActivityRepository activities, ProjectCurrentUserProvider currentUser) {
		this.members = members; this.activities = activities; this.currentUser = currentUser;
	}
	public ProjectActivityPageResponse find(String slug, boolean signedIn, int page, int size) {
		if (page < 0 || size < 1 || size > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지와 조회 개수를 확인해 주세요.");
		Long viewer = signedIn ? currentUser.getCurrentUserId() : null;
		long target;
		if ("me".equals(slug)) {
			if (viewer == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
			target = viewer;
		} else {
			target = members.findByTagAndStatus(slug, Member.MemberStatus.ACTIVE)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다.")).getId();
		}
		return activities.find(target, viewer, page, size);
	}
}
