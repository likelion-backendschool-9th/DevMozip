package com.devmozip.backend.dto.project;

import com.devmozip.backend.utils.StrictStringDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;

public record CreateProjectReportRequest(
	@JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank @Size(max=100) String reason,
	@JsonDeserialize(using = StrictStringDeserializer.class) @Size(max=1000) String detail
) {}
