package com.devmozip.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_recommendation_requests", uniqueConstraints = @UniqueConstraint(
	name = "uk_ai_request_key", columnNames = {"ai_conversation_id", "idempotency_key"}),
	indexes = @Index(name = "ix_ai_request_latest", columnList = "ai_conversation_id, created_at, id"))
@Check(constraints = "(status = 'PENDING' AND completed_at IS NULL) OR (status IN ('SUCCESS', 'FAILED') AND completed_at IS NOT NULL)")
public class AiRecommendationRequest {
	public enum Status { PENDING, SUCCESS, FAILED }
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ai_conversation_id", nullable = false)
	private AiConversation conversation;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ai_message_id")
	private AiMessage message;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String conditions;
	@Column(name = "idempotency_key", nullable = false, length = 128)
	private String idempotencyKey;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
	private Status status;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	protected AiRecommendationRequest() {}
	public AiRecommendationRequest(AiConversation conversation, AiMessage message, String conditions, String key) {
		this.conversation = conversation;
		this.message = message;
		this.conditions = conditions;
		this.idempotencyKey = key;
		this.status = Status.PENDING;
		this.createdAt = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
	}
	public void complete(Status status) {
		if (this.status != Status.PENDING || status == Status.PENDING) throw new IllegalStateException("이미 완료된 추천 요청입니다.");
		this.status = status;
		this.completedAt = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
	}
	public Long getId() { return id; }
	public Long getMessageId() { return message == null ? null : message.getId(); }
	public String getMessageContent() { return message == null ? null : message.getContent(); }
	public String getConditions() { return conditions; }
	public Status getStatus() { return status; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getCompletedAt() { return completedAt; }
}
