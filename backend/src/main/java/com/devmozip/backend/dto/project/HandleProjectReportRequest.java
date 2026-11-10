package com.devmozip.backend.dto.project;
import jakarta.validation.constraints.*;
import com.devmozip.backend.entity.Report;
public record HandleProjectReportRequest(@NotNull Report.Status status,@Size(max=1000) String resolution) {}
