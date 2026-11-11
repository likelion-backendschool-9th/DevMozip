package com.devmozip.backend.dto.notification;

import java.time.LocalDateTime;

/**
 * 알림 목록 항목. token과 review_id는 응답에 넣지 않는다.
 * read는 읽은 시각이 있는지, resolved는 리뷰 작성 완료·기간 만료로 처리가 끝났는지다. 둘은 따로 계산한다.
 * review는 리뷰 알림에만 있고 다른 종류는 null이다.
 */
public record NotificationResponse(long id, String type, String title, String body, String actionUrl, Long projectId,
	boolean read, boolean resolved, Review review, LocalDateTime createdAt) {

	/** status: PENDING(작성 대기), COMPLETED(작성 완료), EXPIRED(기간 만료) */
	public record Review(String status, LocalDateTime expiresAt) {}
}
