package com.devmozip.backend.event;
import java.time.LocalDateTime;

/** 소비자는 AFTER_COMMIT에서 처리하고 eventKey로 중복 제거한다. 외부 알림·채팅·신뢰 점수 계산은 담당자 연동 사항이다. */
public record ProjectApplicationChanged(Type type,long projectId,long applicationId,long applicantId,long hostId,
	String jobRole,boolean penaltyRequired,String eventKey,LocalDateTime occurredAt) {
	public enum Type { SUBMITTED, APPROVED, REJECTED, CANCELLED }
}
