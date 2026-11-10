package com.devmozip.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.devmozip.backend.entity.Comment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {
	/** 게시글의 삭제되지 않은 댓글을 작성 순서대로 읽는다. */
	@Query("select c from Comment c join fetch c.author where c.post.id = :postId and c.deletedAt is null"
		+ " order by c.createdAt asc, c.id asc")
	List<Comment> findLiveByPost(@Param("postId") long postId);

	/** 수정·삭제·신고를 같은 댓글끼리 차례로 처리한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Comment c where c.id = :id and c.deletedAt is null")
	Optional<Comment> findLiveForUpdate(@Param("id") long id);

	/** 게시글별 삭제되지 않은 댓글 수. [post_id, count] 행으로 돌려준다. */
	@Query("select c.post.id, count(c) from Comment c where c.post.id in :postIds and c.deletedAt is null group by c.post.id")
	List<Object[]> countLiveByPosts(@Param("postIds") Collection<Long> postIds);
}
