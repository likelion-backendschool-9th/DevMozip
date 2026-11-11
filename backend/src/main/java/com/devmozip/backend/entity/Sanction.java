package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 제재 내역 (ERD sanctions, docs/admin-plan.md D1). 경고·정지마다 행을 하나 만들고,
 * 해제하면 그 행의 revoked_at을 채운다. 경고에서 정지처럼 상태가 바뀌면 이전 제재를 철회하고 새 행을 만든다.
 * ends_at(제재 종료 시각)은 ERD에 맞춰 두되 기한 있는 제재가 없어 항상 NULL(무기한)이다.
 * 회원의 현재 상태는 users.status다.
 */
@Entity
@Table(name = "sanctions", indexes = @Index(name = "sanctions_user_id_idx", columnList = "user_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sanction {
	public enum Type {
		WARNED,     // 경고
		SUSPENDED   // 정지
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 제재 대상
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private Member user;

	// 근거 신고 (있을 때만)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "report_id")
	private Report report;

	// 처리 관리자
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "admin_id", nullable = false)
	private Member admin;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 30)
	private Type type;

	// 제재 사유. 서비스에서 필수로 검사한다.
	@Column(name = "reason", columnDefinition = "text")
	private String reason;

	// 제재 시작 시각
	@Column(name = "starts_at", nullable = false, updatable = false)
	private LocalDateTime startsAt;

	// 제재 종료 시각. 기한 있는 제재가 없어 NULL(무기한)이다.
	@Column(name = "ends_at")
	private LocalDateTime endsAt;

	// 제재 철회 시각. 해제하거나 다른 제재로 바뀌면 채운다.
	@Column(name = "revoked_at")
	private LocalDateTime revokedAt;

	public static Sanction start(Member user, Member admin, Report report, Type type, String reason, LocalDateTime now) {
		Sanction sanction = new Sanction();
		sanction.user = user;
		sanction.admin = admin;
		sanction.report = report;
		sanction.type = type;
		sanction.reason = reason;
		sanction.startsAt = now;
		return sanction;
	}

	/** 철회한다. 이미 철회한 제재는 그대로 둔다. */
	public void revoke(LocalDateTime now) {
		if (revokedAt == null) {
			revokedAt = now;
		}
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}
}
