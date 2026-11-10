package com.devmozip.backend.service;

import com.devmozip.backend.dto.notification.NotificationPageResponse;
import com.devmozip.backend.dto.notification.NotificationReadAllResponse;
import com.devmozip.backend.dto.notification.NotificationReadRequest;
import com.devmozip.backend.dto.notification.NotificationReadResponse;

/**
 * WBS 12.1~12.5 알림. 알림은 create로만 만들며, 다른 도메인은 알림 테이블에 직접 쓰지 않고 이벤트를 발행한다.
 * 조회·변경의 요청자는 인증 정보로 정하고, 본인 알림만 다룬다.
 */
public interface NotificationService {
	/**
	 * 새 트랜잭션으로 알림을 저장한다. 같은 수신자에게 같은 token의 알림이 있으면(동시에 들어온 경우 포함) 만들지 않고 false다.
	 * 내부 이벤트 처리에서만 부른다.
	 */
	boolean create(NotificationDraft draft);

	/** status: all(기본)·unread·read. unread는 읽지 않았고 처리 완료가 아닌 알림이다. */
	NotificationPageResponse list(String status, int page, int size);

	/** 본문이 없거나 read가 true면 읽음, false면 안 읽음. 다른 사람의 알림은 404다. */
	NotificationReadResponse changeRead(long id, NotificationReadRequest request);

	NotificationReadAllResponse readAll();
}
