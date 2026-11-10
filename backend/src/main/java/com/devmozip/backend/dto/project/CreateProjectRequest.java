package com.devmozip.backend.dto.project;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record CreateProjectRequest(
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
	public record RecruitmentSlot(
		@NotBlank @Size(max = 30) String roleCode,
		@NotNull @Min(1) @Max(10) Integer capacity,
		@NotNull @Min(0) @Max(5) Integer minimumYears
	) {}
}
