package com.devmozip.backend.service;

import com.devmozip.backend.entity.enums.NotificationType;

/**
 * 만들 알림 한 건. NotificationEventHandler가 이벤트에서 만든다.
 * token은 중복 방지 식별값이고, actionUrl은 서버가 만든 내부 경로다.
 */
public record NotificationDraft(long recipientId, Long projectId, Long reviewId, String token, NotificationType type,
	String title, String body, String actionUrl) {}
