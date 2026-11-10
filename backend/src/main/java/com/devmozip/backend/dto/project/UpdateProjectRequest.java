package com.devmozip.backend.dto.project;

import java.time.LocalDate;
import java.util.List;
import com.devmozip.backend.dto.project.CreateProjectRequest.RecruitmentSlot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record UpdateProjectRequest(
	@NotBlank @Size(max = 255) String title,
	@Size(max = 255) String summary,
	@NotBlank String description,
	String goal,
	@NotBlank @Size(max = 2048) String imageUrl,
	@NotNull Boolean isPublic,
	@NotEmpty @Size(max = 10) List<@NotNull @Valid RecruitmentSlot> recruitmentSlots,
	@NotNull @Size(max = 5) List<@NotBlank String> techStacks,
	@NotNull @Size(max = 5) List<@NotBlank String> interests,
	@NotNull LocalDate recruitmentStart,
	@NotNull LocalDate recruitmentEnd,
	@NotNull LocalDate developmentStart,
	@NotNull LocalDate developmentEnd
) {
	public CreateProjectRequest asCreateRequest() {
		return new CreateProjectRequest(title, summary, description, goal, imageUrl, isPublic, recruitmentSlots,
			techStacks, interests, recruitmentStart, recruitmentEnd, developmentStart, developmentEnd);
	}
}
