package com.devmozip.backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devmozip.backend.entity.Payment;
import com.devmozip.backend.entity.enums.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
	/** 13.5 결제 내역: 회원의 모든 구독의 결제를 시도 시각 최신순으로 */
	@Query("select p from Payment p join fetch p.subscription s join fetch s.plan where s.member.id = :memberId order by p.createdAt desc, p.id desc")
	Slice<Payment> findHistory(@Param("memberId") Long memberId, Pageable pageable);

	/** 이용 기간 계산: 이 구독에서 결제를 마친 횟수 */
	long countBySubscriptionIdAndStatus(Long subscriptionId, PaymentStatus status);
}
