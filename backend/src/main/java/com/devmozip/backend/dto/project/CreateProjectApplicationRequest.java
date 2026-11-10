package com.devmozip.backend.dto.project;
import java.util.List;
import jakarta.validation.constraints.*;
public record CreateProjectApplicationRequest(
	@NotBlank @Size(max=30) String jobRole,
	@NotEmpty @Size(max=3) List<@NotNull @Positive Long> portfolioIds,
	@NotBlank @Size(min=10,max=1500) String introduction
) {}
