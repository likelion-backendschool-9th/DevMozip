package com.devmozip.backend.ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_conversations")
public class AiConversation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "user_id", nullable = false)
	private long userId;
	@Column(length = 255)
	private String title;
	@Column(name = "current_conditions")
	@JdbcTypeCode(SqlTypes.JSON)
	private String currentConditions;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected AiConversation() {}
	public AiConversation(long userId, String title, LocalDateTime now) {
		this.userId = userId;
		this.title = title;
		this.createdAt = now;
		this.updatedAt = now;
	}
	public void touch(LocalDateTime now) { updatedAt = now; }
	public Long getId() { return id; }
	public long getUserId() { return userId; }
	public String getTitle() { return title; }
	public String getCurrentConditions() { return currentConditions; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
}
