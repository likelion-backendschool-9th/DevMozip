package com.devmozip.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_messages")
public class AiMessage {
	public enum Role { USER, ASSISTANT }
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conversation_id", nullable = false)
	private AiConversation conversation;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;
	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected AiMessage() {}
	public AiMessage(AiConversation conversation, String content, LocalDateTime now) {
		this.conversation = conversation;
		this.role = Role.USER;
		this.content = content;
		this.createdAt = now;
	}
	public Long getId() { return id; }
	public Role getRole() { return role; }
	public String getContent() { return content; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
