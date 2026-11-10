package com.devmozip.backend.notification;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.devmozip.backend.entity.enums.NotificationType;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.event.ProjectApplicationChanged;
import com.devmozip.backend.event.ProjectLifecycleChanged;
import com.devmozip.backend.service.NotificationMessages;
import com.devmozip.backend.service.NotificationMessages.Text;
import org.junit.jupiter.api.Test;

/** 종류별 문구·링크(docs/notification-plan.md 4.2). DB 없이 실행한다. */
class NotificationMessagesTest {
	@Test
	void applicationTextsFollowMock() {
		assertEquals(new Text(NotificationType.PROJECT_APPLICATION_SUBMITTED, "프로젝트 지원 도착", "백엔드곰님이 지원했습니다.", "/projects/12"),
			NotificationMessages.application(ProjectApplicationChanged.Type.SUBMITTED, 12, "알림 프로젝트", "백엔드곰"));
		assertEquals(new Text(NotificationType.PROJECT_APPLICATION_CANCELLED, "프로젝트 지원 취소", "백엔드곰님이 지원을 취소했습니다.", "/projects/12"),
			NotificationMessages.application(ProjectApplicationChanged.Type.CANCELLED, 12, "알림 프로젝트", "백엔드곰"));
		assertEquals(new Text(NotificationType.PROJECT_APPLICATION_APPROVED, "지원 승인", "알림 프로젝트: 팀원으로 등록되었습니다.", "/projects/12"),
			NotificationMessages.application(ProjectApplicationChanged.Type.APPROVED, 12, "알림 프로젝트", null));
		assertEquals(new Text(NotificationType.PROJECT_APPLICATION_REJECTED, "지원 거절", "알림 프로젝트: 지원이 거절되었습니다.", "/projects/12"),
			NotificationMessages.application(ProjectApplicationChanged.Type.REJECTED, 12, "알림 프로젝트", null));
	}

	@Test
	void missingNamesUseNeutralFallbacks() {
		assertEquals("지원자님이 지원했습니다.", NotificationMessages.application(ProjectApplicationChanged.Type.SUBMITTED, 1, null, " ").body());
		assertEquals("프로젝트: 지원이 거절되었습니다.", NotificationMessages.application(ProjectApplicationChanged.Type.REJECTED, 1, "", null).body());
	}

	@Test
	void lifecycleTextsUseKoreanStatusNames() {
		assertEquals(new Text(NotificationType.PROJECT_STATUS_CHANGED, "프로젝트 상태 변경", "알림 프로젝트: 모집 완료", "/projects/3"),
			NotificationMessages.lifecycle(ProjectLifecycleChanged.Type.STATUS_CHANGED, 3, "알림 프로젝트", ProjectStatus.RECRUITMENT_COMPLETED));
		assertEquals(new Text(NotificationType.PROJECT_PERIOD_EXTENDED, "프로젝트 기간 연장", "알림 프로젝트: 변경된 모집 조건과 기간을 확인해 주세요.", "/projects/3"),
			NotificationMessages.lifecycle(ProjectLifecycleChanged.Type.PERIOD_EXTENDED, 3, "알림 프로젝트", ProjectStatus.RECRUITING));
		assertEquals(new Text(NotificationType.PROJECT_COMPLETED, "프로젝트 종료", "알림 프로젝트 프로젝트가 종료되었습니다.", "/projects/3"),
			NotificationMessages.lifecycle(ProjectLifecycleChanged.Type.COMPLETED, 3, "알림 프로젝트", ProjectStatus.COMPLETED));
		assertEquals("모집 중", NotificationMessages.statusLabel(ProjectStatus.RECRUITING));
		assertEquals("모집 완료", NotificationMessages.statusLabel(ProjectStatus.RECRUITMENT_COMPLETED));
		assertEquals("프로젝트 진행 중", NotificationMessages.statusLabel(ProjectStatus.IN_PROGRESS));
		assertEquals("프로젝트 종료", NotificationMessages.statusLabel(ProjectStatus.COMPLETED));
	}

	@Test
	void reviewTextsLinkWithoutTokenAndShowExpiry() {
		var request = NotificationMessages.review(false, 9, "알림 프로젝트", LocalDateTime.parse("2026-12-06T12:00:00"));
		assertEquals(new Text(NotificationType.PROJECT_REVIEW_REQUESTED, "협업 리뷰 작성 요청",
			"알림 프로젝트: 2026-12-06 12:00까지 본인을 제외한 팀원을 평가해 주세요.", "/projects/9/reviews"), request);
		var reminder = NotificationMessages.review(true, 9, "알림 프로젝트", LocalDateTime.parse("2026-12-06T12:00:00"));
		assertEquals(new Text(NotificationType.PROJECT_REVIEW_REMINDED, "협업 리뷰 작성 재알림",
			"알림 프로젝트: 아직 평가하지 않은 팀원이 있습니다.", "/projects/9/reviews"), reminder);
		assertFalse(request.actionUrl().contains("token"));
		assertTrue(request.type().review() && reminder.type().review());
		assertFalse(NotificationType.PROJECT_COMPLETED.review());
	}

	@Test
	void lifecycleTokenDistinguishesTypeStatusAndTime() {
		var at = LocalDateTime.parse("2026-11-10T10:00:00.123456");
		var changed = new ProjectLifecycleChanged(ProjectLifecycleChanged.Type.STATUS_CHANGED, 4, 1, ProjectStatus.RECRUITING,
			ProjectStatus.RECRUITMENT_COMPLETED, LocalDate.parse("2026-11-20"), LocalDate.parse("2026-11-20"), null, null, at);
		assertEquals("project-lifecycle:4:STATUS_CHANGED:RECRUITMENT_COMPLETED:2026-11-10T10:00:00.123456", NotificationMessages.lifecycleToken(changed));
		var later = new ProjectLifecycleChanged(ProjectLifecycleChanged.Type.STATUS_CHANGED, 4, 1, ProjectStatus.RECRUITMENT_COMPLETED,
			ProjectStatus.RECRUITING, null, null, null, null, at.plusSeconds(1));
		assertNotEquals(NotificationMessages.lifecycleToken(changed), NotificationMessages.lifecycleToken(later));
		assertTrue(NotificationMessages.lifecycleToken(changed).length() <= 200);
	}
}
