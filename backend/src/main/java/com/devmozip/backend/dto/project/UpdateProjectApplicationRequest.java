package com.devmozip.backend.dto.project;
import jakarta.validation.constraints.*;
import com.devmozip.backend.entity.ProjectApplication.Status;
public record UpdateProjectApplicationRequest(@NotNull Status status,@Size(max=200) String rejectionReason,Boolean acknowledgePenalty) {}
