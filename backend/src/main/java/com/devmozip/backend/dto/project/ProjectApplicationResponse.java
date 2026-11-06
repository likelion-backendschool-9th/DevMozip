package com.devmozip.backend.dto.project;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.devmozip.backend.entity.ProjectApplication.Status;
public record ProjectApplicationResponse(long id,long projectId,long applicantMemberId,String nickname,String tag,String jobRole,
	String introduction,Status status,String rejectionReason,List<Long> portfolioIds,LocalDateTime appliedAt,
	LocalDateTime processedAt,LocalDateTime cancelledAt,boolean cancellationPenaltyRequired,LocalDate penaltyFreeThrough) {}
