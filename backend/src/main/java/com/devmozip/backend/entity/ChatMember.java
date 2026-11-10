package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 방별 참여자와 읽음·퇴장 정보. */
@Entity
@Table(name = "chat_members",
	uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "user_id"}),
	indexes = @Index(name = "idx_chat_members_user_id", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMember {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id", nullable = false)
	private ChatRoom room;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private Member member;
	@Column(name = "last_read_at")
	private LocalDateTime lastReadAt;
	@Column(name = "joined_at", nullable = false)
	private LocalDateTime joinedAt;
	@Column(name = "left_at")
	private LocalDateTime leftAt;

	public ChatMember(ChatRoom room, Member member, LocalDateTime now) {
		this.room = room;
		this.member = member;
		this.joinedAt = now;
	}

	/** 방에 들어온 적 없이 제외된 회원. 이 행이 있으면 다음 접근 때 참여 행을 만들지 않는다. */
	public static ChatMember excluded(ChatRoom room, Member member, LocalDateTime now) {
		ChatMember excluded = new ChatMember(room, member, now);
		excluded.leftAt = now;
		return excluded;
	}

	public boolean hasLeft() {
		return leftAt != null;
	}

	/** 퇴장·제외. 이미 나간 회원은 처음 나간 시각을 유지한다. */
	public void leave(LocalDateTime now) {
		if (leftAt == null) {
			leftAt = now;
		}
	}
}
