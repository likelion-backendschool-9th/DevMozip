package com.devmozip.backend.repository;

import java.util.Optional;
import com.devmozip.backend.entity.Report;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 관리자 신고 내역 (WBS 14.8, docs/admin-plan.md 5.7 D5). 다섯 종류를 채워진 대상 칼럼으로 나눈다.
 * PROJECT: project_id만, MEMBER: project_id와 reported_user_id, USER: reported_user_id만, POST: post_id만, COMMENT: comment_id.
 */
public interface AdminReportQueryRepository extends Repository<Report, Long> {
	String TYPE_CONDITION = """
		(:status is null or r.status = :status)
		and (:type is null
			or (:type = 'PROJECT' and r.projectId is not null and r.reportedUser is null and r.postId is null and r.commentId is null)
			or (:type = 'MEMBER' and r.projectId is not null and r.reportedUser is not null and r.postId is null and r.commentId is null)
			or (:type = 'USER' and r.projectId is null and r.reportedUser is not null and r.postId is null and r.commentId is null)
			or (:type = 'POST' and r.postId is not null and r.commentId is null)
			or (:type = 'COMMENT' and r.commentId is not null))""";

	@Query(value = "select r from Report r join fetch r.reporter left join fetch r.reportedUser where " + TYPE_CONDITION + " order by r.id desc",
		countQuery = "select count(r) from Report r where " + TYPE_CONDITION)
	Page<Report> search(@Param("status") Report.Status status, @Param("type") String type, Pageable pageable);

	/** 처리할 신고를 잠근다. 같은 신고의 동시 최종 처리를 한 번만 적용한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Report r where r.id = :id")
	Optional<Report> findForUpdate(@Param("id") long id);
}
