package com.devmozip.backend.entity.enums;

/**
 * 알림 종류(notifications.type, VARCHAR(50)). 이름을 그대로 저장·응답하므로 바꾸면 기존 행과 화면이 함께 바뀐다.
 * 새 종류는 출처 도메인이 이벤트를 발행할 때 NotificationMessages·NotificationEventHandler와 함께 추가한다.
 */
public enum NotificationType {
	PROJECT_APPLICATION_SUBMITTED,
	PROJECT_APPLICATION_CANCELLED,
	PROJECT_APPLICATION_APPROVED,
	PROJECT_APPLICATION_REJECTED,
	PROJECT_STATUS_CHANGED,
	PROJECT_PERIOD_EXTENDED,
	PROJECT_COMPLETED,
	PROJECT_REVIEW_REQUESTED,
	PROJECT_REVIEW_REMINDED;

	/** 리뷰 작성 상태(작성 대기·완료·만료)를 함께 보여 주는 종류 */
	public boolean review() {
		return this == PROJECT_REVIEW_REQUESTED || this == PROJECT_REVIEW_REMINDED;
	}
}
