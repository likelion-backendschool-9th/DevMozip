package com.devmozip.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_messages", uniqueConstraints = @UniqueConstraint(columnNames = {"conversation_id", "client_key"}))
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

	@Column(name = "client_key", length = 128)
	private String clientKey;
	public void setClientKey(String key) { this.clientKey = key; }
	public String getClientKey() { return clientKey; }

	protected AiMessage() {}
	public AiMessage(AiConversation conversation, String content, LocalDateTime now) {
		this.conversation = conversation;
		this.role = Role.USER;
		this.content = content;
		this.createdAt = now;
	}
	public Long getId() { return id; }
	public static AiMessage assistant(AiConversation conversation, String content, String key, LocalDateTime now) {
		var message = new AiMessage(conversation, content, now);
		message.role = Role.ASSISTANT;
		message.clientKey = key;
		return message;
	}
	public Role getRole() { return role; }
	public String getContent() { return content; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
