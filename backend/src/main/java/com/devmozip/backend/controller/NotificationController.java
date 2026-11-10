package com.devmozip.backend.controller;

import com.devmozip.backend.dto.notification.NotificationPageResponse;
import com.devmozip.backend.dto.notification.NotificationReadAllResponse;
import com.devmozip.backend.dto.notification.NotificationReadRequest;
import com.devmozip.backend.dto.notification.NotificationReadResponse;
import com.devmozip.backend.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * WBS 12.1·12.2 알림 목록과 읽음 처리. read-all은 WBS에 없는 API다(docs/notification-plan.md D5).
 * 모든 경로는 로그인이 필요하며(SecurityConfig anyRequest), 요청자는 Service가 인증 정보에서 찾는다.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
	private final NotificationService notifications;

	public NotificationController(NotificationService notifications) {
		this.notifications = notifications;
	}

	@GetMapping
	public NotificationPageResponse list(@RequestParam(required = false) String status,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size) {
		return notifications.list(status, page, size);
	}

	@PatchMapping("/{id}/read")
	public NotificationReadResponse read(@PathVariable long id, @RequestBody(required = false) NotificationReadRequest request) {
		return notifications.changeRead(id, request);
	}

	@PatchMapping("/read-all")
	public NotificationReadAllResponse readAll() {
		return notifications.readAll();
	}
}
