package com.devmozip.backend.dto.ai.internal;

import java.time.LocalDate;
import java.util.List;
import com.devmozip.backend.entity.enums.ProjectStatus;

public record SimilarProjectCandidate(long id, String title, String summary, String description, List<String> techStacks,
	List<String> interests, ProjectStatus status, LocalDate developmentStart, LocalDate developmentEnd) {
	public SimilarProjectCandidate(long id, String title, String description, List<String> techStacks,
		List<String> interests, ProjectStatus status, LocalDate developmentStart, LocalDate developmentEnd) {
		this(id, title, null, description, techStacks, interests, status, developmentStart, developmentEnd);
	}
}
