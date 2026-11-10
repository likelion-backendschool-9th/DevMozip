package com.devmozip.backend.entity;

import java.io.Serializable;
import java.util.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** follows의 복합 기본 키 (follower_id, following_id). */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FollowId implements Serializable {
	// 팔로우하는 사용자 users.id
	@Column(name = "follower_id")
	private Long followerId;

	// 팔로우받는 사용자 users.id
	@Column(name = "following_id")
	private Long followingId;

	public FollowId(Long followerId, Long followingId) {
		this.followerId = followerId;
		this.followingId = followingId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof FollowId id)) return false;
		return Objects.equals(followerId, id.followerId) && Objects.equals(followingId, id.followingId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(followerId, followingId);
	}
}
