package com.devmozip.backend.dto.project;

import java.time.LocalDateTime;
public record ProjectReportResponse(long id, long projectId, String status, LocalDateTime createdAt) {}
