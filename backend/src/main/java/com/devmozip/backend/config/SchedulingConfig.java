package com.devmozip.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled를 켠다. 현재 사용처는 구독 매월 결제(scheduler/SubscriptionRenewalScheduler)뿐이다.
 * 스케줄러가 저장소에 처음 생기는 설정이므로 다른 기능이 쓰기 전에 팀과 공유한다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
