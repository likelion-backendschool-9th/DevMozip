package com.devmozip.backend.repository;

import com.devmozip.backend.entity.Follow;
import com.devmozip.backend.entity.FollowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, FollowId> {
	// 이 사용자를 팔로우하는 사람 수
	@Query("select count(f) from Follow f where f.id.followingId = :userId")
	long countFollowers(@Param("userId") long userId);

	// 이 사용자가 팔로우하는 사람 수
	@Query("select count(f) from Follow f where f.id.followerId = :userId")
	long countFollowing(@Param("userId") long userId);

	// 없는 관계를 지워도 오류 없이 0을 돌려준다. 동시에 온 언팔로우 요청도 그대로 끝난다.
	@Modifying
	@Query("delete from Follow f where f.id.followerId = :followerId and f.id.followingId = :followingId")
	int deleteRelation(@Param("followerId") long followerId, @Param("followingId") long followingId);

	// 바뀐 행 수를 돌려준다. 0이면 팔로우하지 않은 상태다.
	@Modifying
	@Query("update Follow f set f.activityNotificationEnabled = :enabled where f.id.followerId = :followerId and f.id.followingId = :followingId")
	int updateActivityNotification(@Param("followerId") long followerId, @Param("followingId") long followingId,
		@Param("enabled") boolean enabled);
}
