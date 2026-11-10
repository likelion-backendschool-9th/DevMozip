package com.devmozip.backend.dto.search;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.entity.ProjectRecruitmentSlot;

/**
 * 프로젝트 카드 한 장(WBS 6.3). 주최자 회원 id·조회수·북마크 여부는 넣지 않는다.
 * jobRoles는 recruitment_slots의 roleCode 목록(중복 제거)이고, memberCount는 ACTIVE 참여 인원이다.
 */
public record ProjectSearchItemResponse(
	long id,
	String title,
	String summary,
	String imageUrl,
	ProjectSearchStatus status,
	List<String> jobRoles,
	List<String> techStacks,
	List<String> interests,
	int totalCapacity,
	long memberCount,
	LocalDate developmentStart,
	LocalDate developmentEnd,
	LocalDateTime createdAt
) {
	public static ProjectSearchItemResponse from(Project project, long memberCount, LocalDate today) {
		return new ProjectSearchItemResponse(
			project.getId(),
			project.getTitle(),
			project.getSummary(),
			project.getImageUrl(),
			ProjectSearchStatus.of(project.getStatus(), project.getRecruitmentEnd(), today),
			project.getRecruitmentSlots().stream().map(ProjectRecruitmentSlot::roleCode).distinct().toList(),
			List.copyOf(project.getTechStacks()),
			List.copyOf(project.getInterests()),
			project.getTotalCapacity(),
			memberCount,
			project.getDevelopmentStart(),
			project.getDevelopmentEnd(),
			project.getCreatedAt());
	}
}
