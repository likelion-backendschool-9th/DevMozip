package com.devmozip.backend.repository;

import java.util.Collection;
import java.util.List;
import com.devmozip.backend.entity.Bookmark;
import com.devmozip.backend.entity.ProjectMember;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark,Long> {
	boolean existsByUserIdAndProjectId(long userId,long projectId);
	@Modifying @Query("delete from Bookmark b where b.userId=:userId and b.project.id=:projectId")
	int removeProject(@Param("userId") long userId,@Param("projectId") long projectId);
	@Query("select b from Bookmark b join fetch b.project p where b.userId=:userId and b.postId is null and p.deletedAt is null and ((p.isPublic=true and p.isRestricted=false) or exists(select m.id from ProjectMember m where m.project=p and m.userId=:userId and m.status=:status)) order by b.createdAt desc,b.id desc")
	List<Bookmark> visibleProjects(@Param("userId") long userId,@Param("status") ProjectMember.Status status);

	// 게시글 북마크 (docs/feed-plan.md 2절). 프로젝트 북마크 행은 post_id가 비어 있어 아래 조건에 걸리지 않는다.
	boolean existsByUserIdAndPostId(long userId,long postId);
	@Modifying @Query("delete from Bookmark b where b.userId=:userId and b.postId=:postId")
	int removePost(@Param("userId") long userId,@Param("postId") long postId);
	@Query("select b.postId from Bookmark b where b.userId=:userId and b.postId in :postIds")
	List<Long> bookmarkedPostIds(@Param("userId") long userId,@Param("postIds") Collection<Long> postIds);
}
