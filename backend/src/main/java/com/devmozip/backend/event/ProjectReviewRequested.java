package com.devmozip.backend.event;
import java.time.LocalDateTime;
/** 알림 담당자 AFTER_COMMIT 연동 경계. href는 비밀 토큰이므로 로그 출력 금지. 영속 발송은 후속 계약이다. */
public record ProjectReviewRequested(long projectId,long recipientUserId,String href,boolean reminder,String eventKey,LocalDateTime expiresAt){@Override public String toString(){return "ProjectReviewRequested[projectId="+projectId+", recipientUserId="+recipientUserId+", reminder="+reminder+"]";}}
