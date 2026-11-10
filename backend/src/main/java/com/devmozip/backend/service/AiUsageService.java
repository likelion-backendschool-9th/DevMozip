package com.devmozip.backend.service;

import com.devmozip.backend.dto.ai.AiUsageResponse;
import com.devmozip.backend.entity.AiRecommendationRequest;
import com.devmozip.backend.entity.AiUsage;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.security.ai.CurrentUserProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiUsageService {
	public record Admission(long usageId, LocalDateTime receivedAt) {}
	public static class LimitExceeded extends RuntimeException {
		private final AiUsageResponse usage;
		public LimitExceeded(AiUsageResponse usage) {
			super(usage.remainingCount() == 0 ? "이번 달 추천 횟수를 모두 사용했습니다." : "남은 추천 횟수를 처리 중입니다. 완료 후 다시 확인해 주세요.");
			this.usage = usage;
		}
		public AiUsageResponse usage() { return usage; }
	}
	private final AiUsageRepository usages;
	private final AiRecommendationRequestRepository requests;
	private final AiPortfolioAnalysisRepository accounts;
	private final CurrentUserProvider currentUser;
	private final EntityManager entityManager;
	private final Clock clock;
	private final Duration ttl;
	private final TransactionTemplate transaction;

	public AiUsageService(AiUsageRepository usages, AiRecommendationRequestRepository requests,
		AiPortfolioAnalysisRepository accounts, CurrentUserProvider currentUser, EntityManager entityManager,
		@Qualifier("aiUsageClock") Clock clock, @Value("${app.ai.usage.reservation-ttl:PT30M}") Duration ttl,
		PlatformTransactionManager manager) {
		if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("추천 예약 수명은 양수여야 합니다.");
		this.usages = usages; this.requests = requests; this.accounts = accounts; this.currentUser = currentUser;
		this.entityManager = entityManager; this.clock = clock; this.ttl = ttl;
		transaction = new TransactionTemplate(manager);
		transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}
	public LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul")).truncatedTo(ChronoUnit.MICROS); }

	/** 같은 월의 최초 생성 경쟁은 UNIQUE로 해결하고 실패 트랜잭션 밖에서 재조회한다. */
	public Admission prepare(long userId) {
		LocalDateTime received = now();
		String month = YearMonth.from(received).toString();
		long id;
		try {
			id = transaction.execute(tx -> usages.findByUserIdAndUsageMonth(userId, month)
				.orElseGet(() -> usages.saveAndFlush(new AiUsage(userId, month, received))).getId());
		} catch (DataIntegrityViolationException collision) {
			id = transaction.execute(tx -> usages.findByUserIdAndUsageMonth(userId, month)
				.orElseThrow(() -> collision).getId());
		}
		return new Admission(id, received);
	}
	public AiUsageResponse current() {
		long userId = currentUser.getCurrentUserId();
		expire(userId);
		var admission = prepare(userId);
		return transaction.execute(tx -> {
			var usage = lock(admission.usageId());
			expireLocked(usage);
			return AiUsageResponse.from(usage, limit(userId));
		});
	}
	/** 회복은 조회·실행 시 수행한다. 월이 바뀌어도 이전 월의 만료 예약을 회수한다. */
	public void expire(long userId) {
		var ids = transaction.execute(tx -> usages.findByUserIdOrderByIdAsc(userId).stream().map(AiUsage::getId).toList());
		for (long id : ids) transaction.executeWithoutResult(tx -> expireLocked(lock(id)));
	}
	private void expireLocked(AiUsage usage) {
		var time = now();
		for (var request : requests.findByUsage_IdAndStatusOrderByIdAsc(usage.getId(), AiRecommendationRequest.Status.PENDING)) {
			if (!request.getReservationExpiresAt().isAfter(time)) {
				request.complete(AiRecommendationRequest.Status.FAILED, time);
				usage.settle(false, time);
			}
		}
	}
	@Transactional(propagation = Propagation.MANDATORY)
	public void reserve(AiRecommendationRequest request, Admission admission, long userId) {
		var usage = lock(admission.usageId());
		if (usage.getUserId() != userId) throw new IllegalStateException("사용량 소유자가 다릅니다.");
		expireLocked(usage);
		var response = AiUsageResponse.from(usage, limit(userId));
		if (response.availableCount() == 0) throw new LimitExceeded(response);
		usage.reserve(now());
		request.reserve(usage, admission.receivedAt(), now().plus(ttl));
	}
	/** 대화 잠금 뒤 호출. 모든 정산 경로는 사용량 → 요청 순서로 잠근다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean lockActive(AiRecommendationRequest request) {
		if (request.getUsageId() == null) throw new IllegalStateException("신규 추천의 사용량 연결이 없습니다.");
		var usage = lock(request.getUsageId());
		entityManager.refresh(request, LockModeType.PESSIMISTIC_WRITE);
		if (request.getStatus() != AiRecommendationRequest.Status.PENDING) return false;
		if (!request.getReservationExpiresAt().isAfter(now())) {
			request.complete(AiRecommendationRequest.Status.FAILED, now());
			usage.settle(false, now());
			return false;
		}
		return true;
	}
	@Transactional(propagation = Propagation.MANDATORY)
	public void settle(AiRecommendationRequest request, boolean success) {
		var usage = lock(request.getUsageId());
		request.complete(success ? AiRecommendationRequest.Status.SUCCESS : AiRecommendationRequest.Status.FAILED, now());
		usage.settle(success, now());
	}
	@Transactional(propagation = Propagation.MANDATORY)
	public void releaseConversation(long conversationId) {
		var items = requests.findByConversationIdOrderByCreatedAtDescIdDesc(conversationId);
		var pending = items.stream()
			.filter(r -> r.getUsageId() != null && r.getStatus() == AiRecommendationRequest.Status.PENDING)
			.sorted(Comparator.comparing(AiRecommendationRequest::getUsageId).thenComparing(AiRecommendationRequest::getId)).toList();
		for (var request : pending) {
			var usage = lock(request.getUsageId());
			entityManager.refresh(request, LockModeType.PESSIMISTIC_WRITE);
			if (request.getStatus() == AiRecommendationRequest.Status.PENDING) {
				request.complete(AiRecommendationRequest.Status.FAILED, now());
				usage.settle(false, now());
			}
		}
		entityManager.flush();
		// 이후 기존 native DELETE가 지우는 요청을 영속성 컨텍스트에도 남기지 않는다.
		items.forEach(entityManager::detach);
	}
	private AiUsage lock(long id) { return usages.lock(id).orElseThrow(() -> new IllegalStateException("월별 추천 사용량이 없습니다.")); }
	private int limit(long userId) {
		var account = accounts.account(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 사용자를 찾을 수 없습니다."));
		if (!Set.of("ACTIVE", "WARNED").contains(account.status() == null ? "" : account.status()))
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 계정으로 추천을 이용할 수 없습니다.");
		if (!Set.of("PREMIUM", "ADMIN").contains(account.role() == null ? "" : account.role())) return 5;
		var subscription = accounts.latestSubscription(userId);
		if (subscription.isEmpty()) return 5;
		var value = subscription.get();
		var time = now();
		return Set.of("ACTIVE", "RENEWING", "CANCEL_REQUESTED").contains(value.status() == null ? "" : value.status())
			&& value.startsAt() != null && value.periodEnd() != null && !time.isBefore(value.startsAt())
			&& time.isBefore(value.periodEnd()) && (value.endedAt() == null || time.isBefore(value.endedAt())) ? 100 : 5;
	}
}
