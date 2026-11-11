package com.devmozip.backend.service;

import java.util.LinkedHashSet;
import java.util.Set;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.entity.ProjectApplication;
import com.devmozip.backend.entity.ProjectMember;
import com.devmozip.backend.event.ProjectApplicationChanged;
import com.devmozip.backend.event.ProjectLifecycleChanged;
import com.devmozip.backend.event.ProjectReviewNotificationEvent;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.NotificationQueryRepository;
import com.devmozip.backend.repository.ProjectApplicationRepository;
import com.devmozip.backend.repository.ProjectMemberRepository;
import com.devmozip.backend.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 프로젝트·리뷰 이벤트를 받아 수신자를 정하고 NotificationService.create를 부른다(WBS 12.3·12.4).
 * 원래 요청이 커밋된 뒤(AFTER_COMMIT) 처리하므로 롤백된 요청은 알림을 만들지 않는다.
 * 알림 저장이 실패해도 예외를 밖으로 던지지 않아 원래 요청의 결과는 바뀌지 않는다. 실패는 종류·수신자 ID만 로그에 남긴다.
 * 프로젝트 쪽 Repository는 읽기만 한다.
 */
@Component
public class NotificationEventHandler {
	private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);

	private final NotificationService notifications;
	private final ProjectRepository projects;
	private final ProjectMemberRepository projectMembers;
	private final ProjectApplicationRepository applications;
	private final MemberRepository members;
	private final NotificationQueryRepository queries;

	public NotificationEventHandler(NotificationService notifications, ProjectRepository projects,
		ProjectMemberRepository projectMembers, ProjectApplicationRepository applications, MemberRepository members,
		NotificationQueryRepository queries) {
		this.notifications = notifications;
		this.projects = projects;
		this.projectMembers = projectMembers;
		this.applications = applications;
		this.members = members;
		this.queries = queries;
	}

	/** 지원 도착·취소는 주최자, 승인·거절은 지원자가 받는다. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public void onApplicationChanged(ProjectApplicationChanged event) {
		try {
			boolean toHost = event.type() == ProjectApplicationChanged.Type.SUBMITTED || event.type() == ProjectApplicationChanged.Type.CANCELLED;
			// 주최자는 자신의 공고에 지원할 수 없으므로 같은 사람이면 보낼 대상이 없다.
			if (event.hostId() == event.applicantId()) return;
			long recipient = toHost ? event.hostId() : event.applicantId();
			String applicant = toHost ? members.findById(event.applicantId()).map(Member::getNickname).orElse(null) : null;
			var text = NotificationMessages.application(event.type(), event.projectId(), projectTitle(event.projectId()), applicant);
			create(recipient, event.projectId(), null, event.eventKey(), text);
		} catch (RuntimeException e) {
			log.warn("알림 이벤트 처리 실패: event=ProjectApplicationChanged, type={}, reason={}", event.type(), e.getClass().getSimpleName());
		}
	}

	/**
	 * 상태 변경·기간 연장은 참여 중(ACTIVE) 팀원과 검토 대기(PENDING) 지원자, 종료는 참여 중 팀원이 받는다(D6 A).
	 * 행위자 본인은 받지 않는다. 종료 이벤트는 수동 종료와 종료 동의 과반 두 경로에서 발행된다.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public void onLifecycleChanged(ProjectLifecycleChanged event) {
		try {
			Set<Long> recipients = new LinkedHashSet<>();
			for (ProjectMember member : projectMembers.findByProjectId(event.projectId())) {
				if (member.getStatus() == ProjectMember.Status.ACTIVE) recipients.add(member.getUserId());
			}
			if (event.type() != ProjectLifecycleChanged.Type.COMPLETED) {
				for (ProjectApplication application : applications.findByProjectIdOrderByAppliedAtDescIdDesc(event.projectId())) {
					if (application.getStatus() == ProjectApplication.Status.PENDING) recipients.add(application.getApplicantMemberId());
				}
			}
			recipients.remove(event.actorUserId());
			if (recipients.isEmpty()) return;
			var text = NotificationMessages.lifecycle(event.type(), event.projectId(), projectTitle(event.projectId()), event.status());
			String token = NotificationMessages.lifecycleToken(event);
			for (long recipient : recipients) create(recipient, event.projectId(), null, token, text);
		} catch (RuntimeException e) {
			log.warn("알림 이벤트 처리 실패: event=ProjectLifecycleChanged, type={}, reason={}", event.type(), e.getClass().getSimpleName());
		}
	}

	/**
	 * 리뷰 최초 요청·재알림은 이벤트의 수신자(리뷰 작성자)가 받는다(WBS 12.4). 1:1 메시지·단체 채팅방으로는 보내지 않는다.
	 * 링크는 리뷰 화면 경로만 저장하고 이벤트의 href(임시 토큰 포함)는 쓰지도 로그에 남기지도 않는다(D3 A).
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public void onReviewRequested(ProjectReviewNotificationEvent event) {
		try {
			Long reviewId = queries.findReviewAnchor(event.projectId(), event.recipientUserId()).orElse(null);
			var text = NotificationMessages.review(event.reminder(), event.projectId(), projectTitle(event.projectId()), event.expiresAt());
			create(event.recipientUserId(), event.projectId(), reviewId, event.eventKey(), text);
		} catch (RuntimeException e) {
			log.warn("알림 이벤트 처리 실패: event=ProjectReviewNotificationEvent, reminder={}, reason={}", event.reminder(), e.getClass().getSimpleName());
		}
	}

	private String projectTitle(long projectId) {
		return projects.findById(projectId).map(Project::getTitle).orElse(null);
	}

	/** 수신자 한 명의 실패가 다른 수신자의 알림을 막지 않는다. */
	private void create(long recipient, Long projectId, Long reviewId, String token, NotificationMessages.Text text) {
		try {
			notifications.create(new NotificationDraft(recipient, projectId, reviewId, token, text.type(), text.title(), text.body(), text.actionUrl()));
		} catch (RuntimeException e) {
			log.warn("알림 생성 실패: type={}, recipientId={}, reason={}", text.type(), recipient, e.getClass().getSimpleName());
		}
	}
}
