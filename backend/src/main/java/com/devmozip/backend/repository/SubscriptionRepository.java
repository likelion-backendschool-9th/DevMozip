package com.devmozip.backend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devmozip.backend.entity.Subscription;
import com.devmozip.backend.entity.enums.SubscriptionStatus;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
	/** 13.3: 가장 최근 구독 */
	@EntityGraph(attributePaths = "plan")
	Optional<Subscription> findFirstByMemberIdOrderByIdDesc(Long memberId);

	/** 해지·결제 준비: 이용 기간 안의 구독. 회원당 하나만 있다 */
	@EntityGraph(attributePaths = "plan")
	Optional<Subscription> findFirstByMemberIdAndStatusIn(Long memberId, Collection<SubscriptionStatus> statuses);

	boolean existsByMemberIdAndStatusIn(Long memberId, Collection<SubscriptionStatus> statuses);

	/** 스케줄러: 주어진 상태이고 이용 기간이 끝난 구독 */
	@Query("select s.id from Subscription s where s.status = :status and s.currentPeriodEnd <= :now order by s.currentPeriodEnd asc, s.id asc")
	List<Long> findDueIds(@Param("status") SubscriptionStatus status, @Param("now") LocalDateTime now);

	/**
	 * 상태가 from이고 이용 기간이 now에 끝났을 때만 to로 바꾼다. 바꾼 행 수를 돌려준다.
	 * 블루그린 배포 중 두 서버가 같은 구독을 잡아도 1을 받는 쪽은 하나뿐이다(행 잠금으로 직렬화된다).
	 * 기간 조건이 없으면, 먼저 읽어 둔 목록으로 이미 결제를 마치고 ACTIVE로 돌아온 구독을 다시 잡아 두 번 결제한다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Subscription s set s.status = :to where s.id = :id and s.status = :from and s.currentPeriodEnd <= :now")
	int changeStatusIfDue(@Param("id") Long id, @Param("from") SubscriptionStatus from, @Param("to") SubscriptionStatus to,
			@Param("now") LocalDateTime now);

	@EntityGraph(attributePaths = {"member", "plan"})
	Optional<Subscription> findWithMemberAndPlanById(Long id);
}
