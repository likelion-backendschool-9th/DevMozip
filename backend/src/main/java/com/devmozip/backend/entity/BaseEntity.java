package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/** 공통 기록 시각. 별도 테이블을 만들지 않는다. */
@Getter
@MappedSuperclass
public abstract class BaseEntity {
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected BaseEntity() {}
	protected BaseEntity(LocalDateTime now) {
		createdAt = now;
		updatedAt = now;
	}

	protected void touch(LocalDateTime now) { updatedAt = now; }
}
