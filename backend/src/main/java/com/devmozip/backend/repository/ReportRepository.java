package com.devmozip.backend.repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

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

	// 게시글·댓글 신고 (docs/feed-plan.md 4절). 게시글 신고는 post_id만, 댓글 신고는 comment_id만 채운다.
	boolean existsByReporter_IdAndPostIdAndCommentIdIsNull(Long reporterId, Long postId);
	boolean existsByReporter_IdAndCommentId(Long reporterId, Long commentId);

	@Query("select r.postId from Report r where r.reporter.id = :reporterId and r.postId in :postIds and r.commentId is null")
	List<Long> reportedPostIds(@Param("reporterId") long reporterId, @Param("postIds") Collection<Long> postIds);

	@Query("select r.commentId from Report r where r.reporter.id = :reporterId and r.commentId in :commentIds")
	List<Long> reportedCommentIds(@Param("reporterId") long reporterId, @Param("commentIds") Collection<Long> commentIds);
}
