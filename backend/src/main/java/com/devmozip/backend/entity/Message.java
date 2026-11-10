package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 참여자가 보낸 메시지. 방과 보낸 사람은 chatMember를 거쳐 찾는다. */
@Entity
@Table(name = "messages",
	indexes = @Index(name = "idx_messages_chat_member_id_id", columnList = "chat_member_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chat_member_id", nullable = false)
	private ChatMember chatMember;
	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;
	@Column(name = "sent_at", nullable = false)
	private LocalDateTime sentAt;
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	public Message(ChatMember chatMember, String content, LocalDateTime now) {
		this.chatMember = chatMember;
		this.content = content;
		this.sentAt = now;
	}
}
