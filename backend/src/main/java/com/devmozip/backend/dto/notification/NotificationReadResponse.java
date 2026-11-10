package com.devmozip.backend.dto.notification;

/** 읽음 변경 결과와 변경 후 본인의 전체 안 읽음 수 */
public record NotificationReadResponse(long id, boolean read, long unreadCount) {}
