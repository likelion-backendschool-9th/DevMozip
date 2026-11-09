package com.devmozip.backend.event;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.devmozip.backend.entity.enums.ProjectStatus;

/** 후속 수신자는 AFTER_COMMIT으로 구독한다. 영속 outbox/재시도/외부 발송은 결합 단계다. */
public record ProjectLifecycleChanged(
	Type type, long projectId, long actorUserId, ProjectStatus previousStatus, ProjectStatus status,
	LocalDate previousRecruitmentEnd, LocalDate recruitmentEnd,
	LocalDate previousDevelopmentEnd, LocalDate developmentEnd, LocalDateTime occurredAt
) {
	public enum Type { STATUS_CHANGED, PERIOD_EXTENDED, COMPLETED }
}
