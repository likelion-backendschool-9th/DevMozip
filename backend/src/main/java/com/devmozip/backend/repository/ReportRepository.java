package com.devmozip.backend.repository;

import com.devmozip.backend.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
	boolean existsByReporter_IdAndReportedUser_Id(Long reporterId, Long reportedUserId);
	boolean existsByReporter_IdAndProjectIdAndReportedUserIsNullAndPostIdIsNullAndCommentIdIsNull(Long reporterId, Long projectId);
}
