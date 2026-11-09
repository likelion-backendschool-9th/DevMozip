package com.devmozip.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devmozip.backend.entity.SubscriptionPlan;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
	/** 13.1 상품 안내: 판매 중인 상품 */
	List<SubscriptionPlan> findAllByActiveTrueOrderByIdAsc();

	/** 결제 준비: 판매 중인 상품만 결제할 수 있다 */
	Optional<SubscriptionPlan> findByIdAndActiveTrue(Long id);
}
