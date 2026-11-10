package com.devmozip.backend.dto.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Project;

/**
 * 관리자 공고 목록 항목 (WBS 14.4·14.5). isPublic은 주최자의 공개 여부, restricted는 관리자 숨김이다.
 * 주최자가 탈퇴했으면 hostTag가 null이다.
 */
public record AdminProjectResponse(long id, String title, String hostTag, String hostNickname, String status, boolean isPublic,
	boolean restricted, LocalDate recruitmentEnd, LocalDate developmentEnd, LocalDateTime createdAt) {

	public static AdminProjectResponse of(Project project, Member host) {
		return new AdminProjectResponse(project.getId(), project.getTitle(), AdminPostResponse.tagOf(host),
			host == null ? null : host.getNickname(), project.getStatus().name(), project.isPublic(), project.isRestricted(),
			project.getRecruitmentEnd(), project.getDevelopmentEnd(), project.getCreatedAt());
	}
}
