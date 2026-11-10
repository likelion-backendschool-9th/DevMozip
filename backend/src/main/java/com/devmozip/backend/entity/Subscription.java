package com.devmozip.backend.entity;

import java.time.LocalDateTime;

import com.devmozip.backend.entity.enums.SubscriptionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구독 (ERD subscriptions). user_id는 users.id, plan_id는 subscription_plans.id를 참조한다.
 * 이용 기간 끝은 starts_at에 결제 완료 횟수만큼 개월을 더해 정한다. 31일에 시작해도 다음 달 말일 보정이 이어지지 않는다.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private Member member;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "plan_id", nullable = false)
	private SubscriptionPlan plan;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SubscriptionStatus status;

	@Column(name = "starts_at", nullable = false)
	private LocalDateTime startsAt;

	@Column(name = "current_period_end", nullable = false)
	private LocalDateTime currentPeriodEnd;

	@Column(name = "cancel_requested_at")
	private LocalDateTime cancelRequestedAt;

	@Column(name = "ended_at")
	private LocalDateTime endedAt;

	/** 첫 결제에 성공한 구독. 첫 달 기간을 연다 */
	public static Subscription start(Member member, SubscriptionPlan plan, LocalDateTime startsAt) {
		Subscription subscription = new Subscription();
		subscription.member = member;
		subscription.plan = plan;
		subscription.status = SubscriptionStatus.ACTIVE;
		subscription.startsAt = startsAt;
		subscription.currentPeriodEnd = subscription.periodEndAfter(1);
		return subscription;
	}

	/** 결제를 paidCount번 마친 뒤의 이용 기간 끝 */
	public LocalDateTime periodEndAfter(long paidCount) {
		return startsAt.plusMonths(paidCount);
	}

	/** 매월 결제 성공. 기간을 늘리고 다시 이용 중으로 둔다 */
	public void renew(LocalDateTime nextPeriodEnd) {
		this.status = SubscriptionStatus.ACTIVE;
		this.currentPeriodEnd = nextPeriodEnd;
	}

	/** 매월 결제 실패. 구독을 바로 끝낸다 */
	public void cancelByPaymentFailure(LocalDateTime now) {
		this.status = SubscriptionStatus.CANCELED;
		this.endedAt = now;
	}

	/** 해지 요청. 기간 끝까지 이용하고 결제하지 않는다 */
	public void requestCancel(LocalDateTime now) {
		this.status = SubscriptionStatus.CANCEL_REQUESTED;
		this.cancelRequestedAt = now;
	}

	/** 해지 예약 구독의 기간 종료 */
	public void expire(LocalDateTime now) {
		this.status = SubscriptionStatus.EXPIRED;
		this.endedAt = now;
	}
}
