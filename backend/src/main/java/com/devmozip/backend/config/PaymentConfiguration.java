package com.devmozip.backend.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.devmozip.backend.entity.SubscriptionPlan;
import com.devmozip.backend.repository.SubscriptionPlanRepository;

/** 구독·결제 공통 설정 (docs/premium-payment-plan.md) */
@Configuration
public class PaymentConfiguration {
	private static final Logger log = LoggerFactory.getLogger(PaymentConfiguration.class);

	/** 컨테이너 시간대 설정이 없으므로 결제일·이용 기간은 한국 시각으로 계산한다 */
	@Bean("paymentClock")
	Clock paymentClock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}

	/**
	 * 상품이 하나도 없으면 프리미엄 월 상품(₩9,900)을 넣는다. 이미 있으면 건드리지 않는다.
	 * 기능별 한도의 키 이름은 AI 사용량(WBS 10.10) 담당과 맞춘다.
	 */
	@Bean
	ApplicationRunner subscriptionPlanInitializer(SubscriptionPlanRepository plans) {
		return args -> {
			if (plans.count() > 0) return;
			plans.save(SubscriptionPlan.monthly("프리미엄", new BigDecimal("9900.00"), "KRW", Map.of("aiRecommendationsPerMonth", 100)));
			log.info("프리미엄 월 상품을 추가했습니다.");
		};
	}
}
