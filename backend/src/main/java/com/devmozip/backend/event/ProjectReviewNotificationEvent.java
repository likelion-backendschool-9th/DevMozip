package com.devmozip.backend.event;

import java.time.LocalDateTime;

/** 리뷰 최초 요청·재알림의 알림 연동 이벤트. 소비자는 AFTER_COMMIT에서 처리한다.
 * href의 비밀 토큰은 로그에 남기지 않으며, 이 이벤트 자체는 알림 발송 완료를 뜻하지 않는다. */
public record ProjectReviewNotificationEvent(
	long projectId, long recipientUserId, String href, boolean reminder,
	String eventKey, LocalDateTime expiresAt
) {
	@Override
	public String toString() {
		return "ProjectReviewNotificationEvent[projectId=" + projectId
			+ ", recipientUserId=" + recipientUserId + ", reminder=" + reminder + "]";
	}
}
