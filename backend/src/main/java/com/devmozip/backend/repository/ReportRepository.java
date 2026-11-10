package com.devmozip.backend.repository;

import java.util.Optional;
import com.devmozip.backend.entity.Report;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {
	boolean existsByReporter_IdAndReportedUser_Id(Long reporterId, Long reportedUserId);
	boolean existsByReporter_IdAndProjectIdAndReportedUserIsNullAndPostIdIsNullAndCommentIdIsNull(Long reporterId, Long projectId);
	boolean existsByReporter_IdAndReportedUser_IdAndProjectIdIsNullAndPostIdIsNullAndCommentIdIsNull(Long reporterId,Long targetId);
	boolean existsByReporter_IdAndProjectIdAndReportedUser_IdAndPostIdIsNullAndCommentIdIsNull(Long reporterId,Long projectId,Long targetId);
	@Query("select r from Report r where r.projectId is not null and r.postId is null and r.commentId is null and (:status is null or r.status=:status) order by r.id desc")
	Page<Report> findProjectReports(@Param("status") Report.Status status,Pageable page);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Report r where r.id=:id and r.projectId is not null and r.postId is null and r.commentId is null")
	Optional<Report> findProjectReportForUpdate(@Param("id") long id);
}
