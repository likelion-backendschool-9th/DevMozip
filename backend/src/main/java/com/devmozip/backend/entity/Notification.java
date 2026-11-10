package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import com.devmozip.backend.entity.enums.NotificationType;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서비스 알림(ERD notifications). 내부 이벤트 처리(NotificationService)만 만든다.
 * updated_at이 없으므로 BaseEntity를 상속하지 않는다. 문구와 이동 경로는 생성 시점에 서버가 만들어 저장한다.
 * token은 알림 중복을 막는 식별값이며 리뷰 임시 링크의 토큰과 관계없다.
 */
@Entity
@Table(name = "notifications",
	uniqueConstraints = @UniqueConstraint(name = "uk_notifications_recipient_token", columnNames = {"recipient_id", "token"}),
	indexes = @Index(name = "idx_notifications_recipient_created", columnList = "recipient_id, created_at, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {
	public static final int TOKEN_LENGTH = 200;
	public static final int TITLE_LENGTH = 255;
	public static final int ACTION_URL_LENGTH = 2048;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 세 FK는 ON DELETE CASCADE다. 원본 행을 물리 삭제하면 그 알림도 지운다(사용자 결정 2026-11-10).
	// 운영 코드는 회원·프로젝트·리뷰를 물리 삭제하지 않는다(프로젝트는 논리 삭제).

	// 수신 사용자
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recipient_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Member recipient;

	// 관련 프로젝트. 프로젝트·리뷰 알림에만 있다
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id")
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Project project;

	// 리뷰 요청. 리뷰 알림에만 있으며, 그 프로젝트에서 수신자가 작성자인 리뷰 행 중 가장 작은 id다
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "review_id")
	@OnDelete(action = OnDeleteAction.CASCADE)
	private ProjectReview review;

	// 식별값(중복 방지)
	@Column(name = "token", nullable = false, length = TOKEN_LENGTH)
	private String token;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 50)
	private NotificationType type;

	@Column(name = "title", nullable = false, length = TITLE_LENGTH)
	private String title;

	@Column(name = "body", nullable = false, columnDefinition = "TEXT")
	private String body;

	// 서버가 만든 내부 경로
	@Column(name = "action_url", nullable = false, length = ACTION_URL_LENGTH)
	private String actionUrl;

	// 읽은 시각. 없으면 안 읽음
	@Column(name = "read_at")
	private LocalDateTime readAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public Notification(Member recipient, Project project, ProjectReview review, String token, NotificationType type,
		String title, String body, String actionUrl, LocalDateTime now) {
		this.recipient = recipient;
		this.project = project;
		this.review = review;
		this.token = token;
		this.type = type;
		this.title = title;
		this.body = body;
		this.actionUrl = actionUrl;
		this.createdAt = now;
	}

	/** 처음 읽은 시각을 유지한다. 반복 요청해도 결과가 같다. */
	public void markRead(LocalDateTime now) {
		if (readAt == null) readAt = now;
	}

	public void markUnread() {
		readAt = null;
	}

	public boolean isRead() {
		return readAt != null;
	}
}
