package com.devmozip.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_usages", uniqueConstraints = @UniqueConstraint(name = "uk_ai_usage_month", columnNames = {"user_id", "usage_month"}))
@Check(constraints = "used_count >= 0 AND pending_count >= 0")
public class AiUsage {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "user_id", nullable = false)
	private long userId;
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "usage_month", nullable = false, length = 7, columnDefinition = "char(7)")
	private String usageMonth;
	@Column(name = "used_count", nullable = false)
	private int usedCount;
	@Column(name = "pending_count", nullable = false)
	private int pendingCount;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected AiUsage() {}
	public AiUsage(long userId, String month, LocalDateTime now) {
		this.userId = userId; this.usageMonth = month; this.createdAt = now; this.updatedAt = now;
	}
	public void reserve(LocalDateTime now) { pendingCount++; updatedAt = now; }
	public void settle(boolean success, LocalDateTime now) {
		if (pendingCount <= 0) throw new IllegalStateException("반환할 추천 예약이 없습니다.");
		pendingCount--;
		if (success) usedCount++;
		updatedAt = now;
	}
	public Long getId() { return id; }
	public long getUserId() { return userId; }
	public String getUsageMonth() { return usageMonth; }
	public int getUsedCount() { return usedCount; }
	public int getPendingCount() { return pendingCount; }
}
