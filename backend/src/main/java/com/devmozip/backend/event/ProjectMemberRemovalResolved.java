package com.devmozip.backend.event;
import java.time.LocalDateTime;
import java.util.List;
/** 실제 알림 발송 담당자가 AFTER_COMMIT에 소비할 결과 계약. 발송 완료를 뜻하지 않는다. */
public record ProjectMemberRemovalResolved(long projectId,long voteId,long targetUserId,String status,List<Long> recipients,String eventKey,LocalDateTime occurredAt) {}
