package com.devmozip.backend.dto.project;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.devmozip.backend.entity.enums.ProjectStatus;

public record ProjectActivityResponse(long projectId, String title, String imageUrl, ProjectStatus status,
	LocalDate developmentStart, LocalDate developmentEnd, LocalDateTime completedAt,
	String role, String jobRole, String membershipStatus) {}
