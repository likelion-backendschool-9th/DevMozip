package com.devmozip.backend.repository;

import java.util.Collection;
import java.util.List;
import com.devmozip.backend.entity.Like;
import com.devmozip.backend.entity.ProjectMember;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface LikeRepository extends JpaRepository<Like,Long> {
	boolean existsByUserIdAndProjectId(long userId,long projectId);
	@Modifying @Query("delete from Like b where b.userId=:userId and b.project.id=:projectId")
	int removeProject(@Param("userId") long userId,@Param("projectId") long projectId);
	@Query("select b from Like b join fetch b.project p where b.userId=:userId and b.postId is null and p.deletedAt is null and ((p.isPublic=true and p.isRestricted=false) or exists(select m.id from ProjectMember m where m.project=p and m.userId=:userId and m.status=:status)) order by b.createdAt desc,b.id desc")
	List<Like> visibleProjects(@Param("userId") long userId,@Param("status") ProjectMember.Status status);

	// 게시글 좋아요 (docs/feed-plan.md 2절). 프로젝트 좋아요 행은 post_id가 비어 있어 아래 조건에 걸리지 않는다.
	boolean existsByUserIdAndPostId(long userId,long postId);
	long countByPostId(long postId);
	@Modifying @Query("delete from Like l where l.userId=:userId and l.postId=:postId")
	int removePost(@Param("userId") long userId,@Param("postId") long postId);
	/** 게시글별 좋아요 수. [post_id, count] 행으로 돌려준다. */
	@Query("select l.postId,count(l) from Like l where l.postId in :postIds group by l.postId")
	List<Object[]> countByPosts(@Param("postIds") Collection<Long> postIds);
	@Query("select l.postId from Like l where l.userId=:userId and l.postId in :postIds")
	List<Long> likedPostIds(@Param("userId") long userId,@Param("postIds") Collection<Long> postIds);
}
