package com.devmozip.backend.repository;

import java.util.List;
import com.devmozip.backend.entity.Payment;
import com.devmozip.backend.entity.Subscription;
import com.devmozip.backend.entity.enums.PaymentStatus;
import com.devmozip.backend.entity.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 관리자 구독·결제 조회 (WBS 14.12·14.13). 프리미엄 결제 도메인의 테이블을 읽기만 하고 포트원을 부르지 않는다.
 * 탈퇴로 끝난 구독과 탈퇴 회원의 결제 기록도 포함한다(#135).
 */
public interface AdminBillingQueryRepository extends Repository<Subscription, Long> {
	@Query(value = "select s from Subscription s join fetch s.member join fetch s.plan where (:status is null or s.status = :status) order by s.id desc",
		countQuery = "select count(s) from Subscription s where (:status is null or s.status = :status)")
	Page<Subscription> searchSubscriptions(@Param("status") SubscriptionStatus status, Pageable pageable);

	/** 상태별 구독 수. [상태, 수] 쌍이다. */
	@Query("select s.status, count(s) from Subscription s group by s.status")
	List<Object[]> countSubscriptionsByStatus();

	@Query(value = """
		select p from Payment p join fetch p.subscription s join fetch s.member
		where (:status is null or p.status = :status) order by p.id desc""",
		countQuery = "select count(p) from Payment p where (:status is null or p.status = :status)")
	Page<Payment> searchPayments(@Param("status") PaymentStatus status, Pageable pageable);

	/** 결제 완료(PAID) 금액의 통화별 합. [통화, 합] 쌍이다. */
	@Query("select p.currency, sum(p.amount) from Payment p where p.status = com.devmozip.backend.entity.enums.PaymentStatus.PAID group by p.currency order by p.currency")
	List<Object[]> paidTotals();
}
