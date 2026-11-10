package com.devmozip.backend.repository;

import java.util.List;
import com.devmozip.backend.entity.Like;
import com.devmozip.backend.entity.ProjectMember;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface LikeRepository extends JpaRepository<Like,Long> {
	boolean existsByUserIdAndProjectId(long userId,long projectId);
	@Modifying @Query("delete from Like b where b.userId=:userId and b.project.id=:projectId")
	int removeProject(@Param("userId") long userId,@Param("projectId") long projectId);
	@Query("select b from Like b join fetch b.project p where b.userId=:userId and b.postId is null and p.deletedAt is null and (p.isPublic=true or exists(select m.id from ProjectMember m where m.project=p and m.userId=:userId and m.status=:status)) order by b.createdAt desc,b.id desc")
	List<Like> visibleProjects(@Param("userId") long userId,@Param("status") ProjectMember.Status status);
}
