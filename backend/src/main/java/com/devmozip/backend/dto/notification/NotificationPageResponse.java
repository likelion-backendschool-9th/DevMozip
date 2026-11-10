package com.devmozip.backend.dto.notification;

import java.util.List;

/** 알림 목록 한 페이지. unreadCount는 탭·페이지와 관계없는 본인의 전체 안 읽음 수이며 헤더 배지도 이 값을 쓴다. */
public record NotificationPageResponse(List<NotificationResponse> items, int page, int size, boolean hasNext, long unreadCount) {}
