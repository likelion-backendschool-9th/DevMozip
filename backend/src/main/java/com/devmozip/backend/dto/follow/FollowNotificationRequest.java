package com.devmozip.backend.dto.follow;

import jakarta.validation.constraints.NotNull;

/** PUT /api/v1/users/{slug}/notify 요청. 이 값 하나로 새 게시글·새 프로젝트 모집 알림을 함께 켜고 끈다. */
public record FollowNotificationRequest(
	@NotNull(message = "알림 설정 값을 선택해 주세요.")
	Boolean activityNotificationEnabled
) {}
