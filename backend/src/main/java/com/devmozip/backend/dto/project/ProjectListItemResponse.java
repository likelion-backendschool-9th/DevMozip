package com.devmozip.backend.dto.project;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.entity.ProjectRecruitmentSlot;
import com.devmozip.backend.entity.enums.ProjectStatus;

public record ProjectListItemResponse(
	long id, long hostMemberId, String title, String summary, String imageUrl, boolean isPublic,
	ProjectStatus status, List<ProjectRecruitmentSlot> recruitmentSlots, List<String> techStacks,
	List<String> interests, int totalCapacity, long viewCount, long memberCount, String myRole,
	LocalDate recruitmentStart, LocalDate recruitmentEnd, LocalDate developmentStart, LocalDate developmentEnd,
	boolean recruitmentClosed, LocalDateTime createdAt
) {
	public static ProjectListItemResponse from(Project project, long memberCount, String myRole, LocalDate today) {
		return new ProjectListItemResponse(project.getId(), project.getHostMemberId(), project.getTitle(), project.getSummary(),
			project.getImageUrl(), project.isPublic(), project.getStatus(), project.getRecruitmentSlots(), project.getTechStacks(),
			project.getInterests(), project.getTotalCapacity(), project.getViewCount(), memberCount, myRole, project.getRecruitmentStart(),
			project.getRecruitmentEnd(), project.getDevelopmentStart(), project.getDevelopmentEnd(),
			project.getStatus() != ProjectStatus.RECRUITING || project.getRecruitmentEnd().isBefore(today), project.getCreatedAt());
	}
}
