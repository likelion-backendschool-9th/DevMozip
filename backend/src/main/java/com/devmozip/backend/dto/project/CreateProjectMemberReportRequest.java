package com.devmozip.backend.dto.project;
import jakarta.validation.constraints.*;
public record CreateProjectMemberReportRequest(@NotBlank String reason,@Size(max=1000) String detail) {}
