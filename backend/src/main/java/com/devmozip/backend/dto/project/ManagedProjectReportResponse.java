package com.devmozip.backend.dto.project;
import java.time.LocalDateTime;
public record ManagedProjectReportResponse(long id,long projectId,String projectTitle,String type,String reporterNickname,String targetNickname,String reason,String status,Long handledBy,String resolution,LocalDateTime handledAt,LocalDateTime createdAt) {}
