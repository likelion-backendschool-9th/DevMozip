package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import com.devmozip.backend.dto.notification.NotificationPageResponse;
import com.devmozip.backend.dto.notification.NotificationReadAllResponse;
import com.devmozip.backend.dto.notification.NotificationReadRequest;
import com.devmozip.backend.dto.notification.NotificationReadResponse;
import com.devmozip.backend.dto.notification.NotificationResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Notification;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.NotificationQueryRepository;
import com.devmozip.backend.repository.NotificationQueryRepository.Filter;
import com.devmozip.backend.repository.NotificationRepository;
import com.devmozip.backend.repository.ProjectRepository;
import com.devmozip.backend.repository.ProjectReviewRepository;
import com.devmozip.backend.security.member.CurrentMemberProvider;
import com.devmozip.backend.service.NotificationDraft;
import com.devmozip.backend.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * 알림 생성·목록·읽음 처리. 시각은 프로젝트·리뷰와 같은 projectClock(한국 시각)으로 정해 리뷰 만료 경계와 맞춘다.
 * 로그에는 알림 ID·종류·수신자 ID만 남기고 본문과 링크는 남기지 않는다.
 */
@Service
public class NotificationServiceImpl implements NotificationService {
	private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);
	static final int DEFAULT_SIZE = 20;
	static final int MAX_SIZE = 100;

	private final NotificationRepository notifications;
	private final NotificationQueryRepository queries;
	private final MemberRepository members;
	private final ProjectRepository projects;
	private final ProjectReviewRepository reviews;
	private final CurrentMemberProvider currentMember;
	private final Clock clock;
	private final TransactionTemplate writeTransaction;
	private final TransactionTemplate readTransaction;

	public NotificationServiceImpl(NotificationRepository notifications, NotificationQueryRepository queries,
		MemberRepository members, ProjectRepository projects, ProjectReviewRepository reviews,
		CurrentMemberProvider currentMember, @Qualifier("projectClock") Clock clock, PlatformTransactionManager transactionManager) {
		this.notifications = notifications;
		this.queries = queries;
		this.members = members;
		this.projects = projects;
		this.reviews = reviews;
		this.currentMember = currentMember;
		this.clock = clock;
		// 이벤트 처리(AFTER_COMMIT)에서도 원래 트랜잭션과 무관하게 커밋되도록 항상 새 트랜잭션을 연다.
		this.writeTransaction = new TransactionTemplate(transactionManager);
		this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.readTransaction = new TransactionTemplate(transactionManager);
		this.readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.readTransaction.setReadOnly(true);
	}

	@Override
	public boolean create(NotificationDraft draft) {
		validate(draft);
		Long id;
		try {
			id = writeTransaction.execute(status -> {
				if (notifications.existsByRecipientIdAndToken(draft.recipientId(), draft.token())) return null;
				var saved = notifications.saveAndFlush(new Notification(
					members.getReferenceById(draft.recipientId()),
					draft.projectId() == null ? null : projects.getReferenceById(draft.projectId()),
					draft.reviewId() == null ? null : reviews.getReferenceById(draft.reviewId()),
					draft.token(), draft.type(), draft.title(), draft.body(), draft.actionUrl(), LocalDateTime.now(clock)));
				return saved.getId();
			});
		} catch (DataIntegrityViolationException e) {
			// 같은 알림이 동시에 저장되면 유일 제약에 걸린다. 그 경우만 이미 만든 알림으로 보고 무시한다.
			if (Boolean.TRUE.equals(readTransaction.execute(status ->
				notifications.existsByRecipientIdAndToken(draft.recipientId(), draft.token())))) return false;
			throw e;
		}
		if (id == null) return false;
		log.info("알림 생성: id={}, type={}, recipientId={}", id, draft.type(), draft.recipientId());
		return true;
	}

	@Override
	@Transactional(readOnly = true)
	public NotificationPageResponse list(String status, int page, int size) {
		Filter filter = filter(status);
		if (page < 0 || size < 1 || size > MAX_SIZE) throw bad("조회 조건을 확인해 주세요.");
		long memberId = requester().getId();
		LocalDateTime now = LocalDateTime.now(clock);
		var slice = queries.find(memberId, filter, page, size, now);
		var items = slice.rows().stream().map(row -> item(row, now)).toList();
		return new NotificationPageResponse(items, page, size, slice.hasNext(), queries.countUnread(memberId, now));
	}

	@Override
	@Transactional
	public NotificationReadResponse changeRead(long id, NotificationReadRequest request) {
		boolean read = request == null || readValue(request);
		long memberId = requester().getId();
		Notification notification = notifications.findByIdAndRecipientId(id, memberId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."));
		// 리뷰 알림의 읽음은 리뷰 작성 상태를 바꾸지 않는다(WBS 12.2).
		LocalDateTime now = LocalDateTime.now(clock);
		if (read) notification.markRead(now);
		else notification.markUnread();
		notifications.flush();
		return new NotificationReadResponse(notification.getId(), notification.isRead(), queries.countUnread(memberId, now));
	}

	@Override
	@Transactional
	public NotificationReadAllResponse readAll() {
		long memberId = requester().getId();
		LocalDateTime now = LocalDateTime.now(clock);
		notifications.markAllRead(memberId, now);
		return new NotificationReadAllResponse(queries.countUnread(memberId, now));
	}

	/** 리뷰 상태는 SQL의 처리 완료 조건(NotificationQueryRepository)과 같은 기준으로 정한다. */
	private NotificationResponse item(NotificationQueryRepository.Row row, LocalDateTime now) {
		NotificationResponse.Review review = null;
		if (row.reviewId() != null) {
			String status = row.pendingReviews() != null && row.pendingReviews() == 0 ? "COMPLETED"
				: row.reviewExpiresAt() != null && !now.isBefore(row.reviewExpiresAt()) ? "EXPIRED" : "PENDING";
			review = new NotificationResponse.Review(status, row.reviewExpiresAt());
		}
		boolean resolved = review != null && !"PENDING".equals(review.status());
		return new NotificationResponse(row.id(), row.type(), row.title(), row.body(), row.actionUrl(), row.projectId(),
			row.readAt() != null, resolved, review, row.createdAt());
	}

	private Member requester() {
		return currentMember.find()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
	}

	private static Filter filter(String status) {
		if (status == null) return Filter.ALL;
		return switch (status) {
			case "all" -> Filter.ALL;
			case "unread" -> Filter.UNREAD;
			case "read" -> Filter.READ;
			default -> throw bad("조회 조건을 확인해 주세요.");
		};
	}

	private static boolean readValue(NotificationReadRequest request) {
		if (request.read() == null) throw bad("읽음 여부(read)를 true 또는 false로 보내 주세요.");
		return request.read();
	}

	private static void validate(NotificationDraft draft) {
		if (draft == null || draft.recipientId() <= 0 || draft.type() == null
			|| draft.projectId() != null && draft.projectId() <= 0 || draft.reviewId() != null && draft.reviewId() <= 0
			|| blank(draft.token()) || draft.token().length() > Notification.TOKEN_LENGTH
			|| blank(draft.title()) || draft.title().length() > Notification.TITLE_LENGTH
			|| blank(draft.body()) || !internalPath(draft.actionUrl())) {
			throw new IllegalArgumentException("알림 생성 값이 올바르지 않습니다.");
		}
	}

	/** 서버가 만든 내부 경로만 저장한다. //host, /\host처럼 다른 사이트로 해석될 수 있는 값과 공백·제어 문자를 막는다. */
	static boolean internalPath(String path) {
		if (path == null || path.isEmpty() || path.length() > Notification.ACTION_URL_LENGTH || path.charAt(0) != '/') return false;
		if (path.length() > 1 && (path.charAt(1) == '/' || path.charAt(1) == '\\')) return false;
		return path.chars().noneMatch(c -> c <= 0x20 || c == 0x7f || c == '\\');
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private static ResponseStatusException bad(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
