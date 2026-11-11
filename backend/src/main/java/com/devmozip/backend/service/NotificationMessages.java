package com.devmozip.backend.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.devmozip.backend.entity.enums.NotificationType;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.event.ProjectApplicationChanged;
import com.devmozip.backend.event.ProjectLifecycleChanged;

/**
 * 알림 종류별 제목·본문·이동 경로(docs/notification-plan.md 4.2). 문구는 Mock 알림을 따른다.
 * 상태가 없는 클래스이며, 생성 시점의 프로젝트 이름·닉네임으로 만든 문구를 그대로 저장한다.
 */
public final class NotificationMessages {
	private static final DateTimeFormatter EXPIRES_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	public record Text(NotificationType type, String title, String body, String actionUrl) {}

	private NotificationMessages() {}

	public static Text application(ProjectApplicationChanged.Type change, long projectId, String projectTitle, String applicantNickname) {
		String project = projectName(projectTitle);
		String applicant = nickname(applicantNickname);
		String path = projectPath(projectId);
		return switch (change) {
			case SUBMITTED -> new Text(NotificationType.PROJECT_APPLICATION_SUBMITTED, "프로젝트 지원 도착", applicant + "님이 지원했습니다.", path);
			case CANCELLED -> new Text(NotificationType.PROJECT_APPLICATION_CANCELLED, "프로젝트 지원 취소", applicant + "님이 지원을 취소했습니다.", path);
			case APPROVED -> new Text(NotificationType.PROJECT_APPLICATION_APPROVED, "지원 승인", project + ": 팀원으로 등록되었습니다.", path);
			case REJECTED -> new Text(NotificationType.PROJECT_APPLICATION_REJECTED, "지원 거절", project + ": 지원이 거절되었습니다.", path);
		};
	}

	public static Text lifecycle(ProjectLifecycleChanged.Type change, long projectId, String projectTitle, ProjectStatus status) {
		String project = projectName(projectTitle);
		String path = projectPath(projectId);
		return switch (change) {
			case STATUS_CHANGED -> new Text(NotificationType.PROJECT_STATUS_CHANGED, "프로젝트 상태 변경", project + ": " + statusLabel(status), path);
			case PERIOD_EXTENDED -> new Text(NotificationType.PROJECT_PERIOD_EXTENDED, "프로젝트 기간 연장", project + ": 변경된 모집 조건과 기간을 확인해 주세요.", path);
			case COMPLETED -> new Text(NotificationType.PROJECT_COMPLETED, "프로젝트 종료", project + " 프로젝트가 종료되었습니다.", path);
		};
	}

	/** 리뷰 요청·재알림. 링크에는 리뷰 임시 토큰을 넣지 않는다(D3 A). 만료일은 리뷰 도메인이 정한 이벤트의 expiresAt이다. */
	public static Text review(boolean reminder, long projectId, String projectTitle, LocalDateTime expiresAt) {
		String project = projectName(projectTitle);
		String path = reviewPath(projectId);
		if (reminder) return new Text(NotificationType.PROJECT_REVIEW_REMINDED, "협업 리뷰 작성 재알림", project + ": 아직 평가하지 않은 팀원이 있습니다.", path);
		String until = expiresAt == null ? "작성 기한 안에" : EXPIRES_AT.format(expiresAt) + "까지";
		return new Text(NotificationType.PROJECT_REVIEW_REQUESTED, "협업 리뷰 작성 요청", project + ": " + until + " 본인을 제외한 팀원을 평가해 주세요.", path);
	}

	/** ProjectLifecycleChanged에는 eventKey가 없어 프로젝트 ID·종류·변경 후 상태·발생 시각으로 만든다. */
	public static String lifecycleToken(ProjectLifecycleChanged event) {
		return "project-lifecycle:" + event.projectId() + ":" + event.type() + ":" + event.status() + ":" + event.occurredAt();
	}

	/** 화면과 같은 한글 상태 이름 */
	public static String statusLabel(ProjectStatus status) {
		if (status == null) return "상태 변경";
		return switch (status) {
			case RECRUITING -> "모집 중";
			case RECRUITMENT_COMPLETED -> "모집 완료";
			case IN_PROGRESS -> "프로젝트 진행 중";
			case COMPLETED -> "프로젝트 종료";
		};
	}

	public static String projectPath(long projectId) {
		return "/projects/" + projectId;
	}

	public static String reviewPath(long projectId) {
		return "/projects/" + projectId + "/reviews";
	}

	private static String projectName(String title) {
		return title == null || title.isBlank() ? "프로젝트" : title.strip();
	}

	private static String nickname(String nickname) {
		return nickname == null || nickname.isBlank() ? "지원자" : nickname.strip();
	}
}
