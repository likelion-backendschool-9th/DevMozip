package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.domain.Persistable;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 팔로우 관계. updated_at이 없으므로 BaseEntity를 상속하지 않는다.
 * 기본 키가 follower_id부터 시작하므로 팔로워 목록을 찾을 때 쓰는 following_id 인덱스를 따로 둔다.
 */
@Entity
@Table(name = "follows", indexes = @Index(name = "idx_follows_following_id", columnList = "following_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow implements Persistable<FollowId> {
	@EmbeddedId
	private FollowId id;

	// 팔로우하는 사용자
	@MapsId("followerId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "follower_id", nullable = false)
	private Member follower;

	// 팔로우받는 사용자
	@MapsId("followingId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "following_id", nullable = false)
	private Member following;

	// 상대의 활동(새 게시글·새 프로젝트 모집) 알림을 받을지 여부. 팔로우 직후에는 꺼져 있다.
	@ColumnDefault("false")
	@Column(name = "activity_notification_enabled", nullable = false)
	private boolean activityNotificationEnabled;

	// 팔로우한 시각
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	// 키를 직접 정하는 엔티티라서, 저장할 때 조회(merge) 없이 바로 INSERT하도록 새 엔티티 여부를 따로 둔다.
	@Transient
	@Getter(AccessLevel.NONE)
	private boolean newEntity = true;

	public static Follow create(Member follower, Member following, LocalDateTime now) {
		Follow follow = new Follow();
		follow.id = new FollowId(follower.getId(), following.getId());
		follow.follower = follower;
		follow.following = following;
		follow.activityNotificationEnabled = false;
		follow.createdAt = now;
		return follow;
	}

	@Override
	public boolean isNew() {
		return newEntity;
	}

	@PostLoad
	@PostPersist
	void markStored() {
		newEntity = false;
	}
}
