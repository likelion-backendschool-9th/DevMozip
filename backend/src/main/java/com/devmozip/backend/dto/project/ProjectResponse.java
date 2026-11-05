package com.devmozip.backend.dto.project;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.entity.ProjectRecruitmentSlot;
import com.devmozip.backend.entity.enums.ProjectStatus;

public record ProjectResponse(
	long id, long hostMemberId, String title, String summary, String description, String goal,
	String imageUrl, boolean isPublic, ProjectStatus status, List<ProjectRecruitmentSlot> recruitmentSlots,
	List<String> techStacks, List<String> interests, int totalCapacity, long viewCount,
	LocalDate recruitmentStart, LocalDate recruitmentEnd, LocalDate developmentStart, LocalDate developmentEnd,
	LocalDateTime createdAt, LocalDateTime updatedAt
) {
	public static ProjectResponse from(Project project) {
		return new ProjectResponse(project.getId(), project.getHostMemberId(), project.getTitle(), project.getSummary(),
			project.getDescription(), project.getGoal(), project.getImageUrl(), project.isPublic(), project.getStatus(),
			project.getRecruitmentSlots(), project.getTechStacks(), project.getInterests(), project.getTotalCapacity(), project.getViewCount(),
			project.getRecruitmentStart(), project.getRecruitmentEnd(), project.getDevelopmentStart(), project.getDevelopmentEnd(),
			project.getCreatedAt(), project.getUpdatedAt());
	}
}
