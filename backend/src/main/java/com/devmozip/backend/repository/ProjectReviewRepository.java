package com.devmozip.backend.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.devmozip.backend.entity.ProjectReview;
public interface ProjectReviewRepository extends JpaRepository<ProjectReview,Long> {
 List<ProjectReview> findByProjectIdOrderByIdAsc(long projectId);
 List<ProjectReview> findByRevieweeUserIdAndStatusOrderBySubmittedAtDesc(long userId,ProjectReview.Status status);
}
