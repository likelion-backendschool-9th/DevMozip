package com.devmozip.backend.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.devmozip.backend.service.SubscriptionRenewalService;
import com.devmozip.backend.service.SubscriptionRenewalService.RenewalResult;

/**
 * 매월 결제·해지 예약 만료를 매일 한 번 실행한다. 실행 시각은 payment.renewal-cron(기본 매일 0시, 한국 시각)이다.
 * 실행 시각에 서버가 멈춰 있었으면 다음 실행에서 지난 결제일의 구독까지 함께 처리한다.
 * 블루그린 배포 중 두 서버가 함께 실행해도 서비스가 한 구독을 한 번만 처리한다.
 */
@Component
@ConditionalOnProperty(name = "payment.renewal-enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionRenewalScheduler {
	private static final Logger log = LoggerFactory.getLogger(SubscriptionRenewalScheduler.class);

	private final SubscriptionRenewalService service;
	private final Clock clock;

	public SubscriptionRenewalScheduler(SubscriptionRenewalService service, @Qualifier("paymentClock") Clock clock) {
		this.service = service;
		this.clock = clock;
	}

	@Scheduled(cron = "${payment.renewal-cron:0 0 0 * * *}", zone = "Asia/Seoul")
	public void run() {
		try {
			RenewalResult result = service.run(LocalDateTime.now(clock));
			if (!result.isEmpty()) log.info("구독 처리: 결제 {}건, 실패 취소 {}건, 결과 미확인 {}건, 해지 만료 {}건, 미룸 {}건",
					result.renewed(), result.failed(), result.unknown(), result.expired(), result.skipped());
		} catch (RuntimeException e) {
			// 한 번 실패해도 다음 주기에 다시 실행된다.
			log.error("구독 처리 실패: {}", e.getClass().getSimpleName());
		}
	}
}
