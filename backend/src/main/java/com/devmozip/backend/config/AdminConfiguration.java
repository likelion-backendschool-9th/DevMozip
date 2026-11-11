package com.devmozip.backend.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 관리자 (docs/admin-plan.md). 제재·처리 시각과 이번 달 기준은 한국 시각으로 정한다. */
@Configuration(proxyBeanMethods = false)
public class AdminConfiguration {
	@Bean("adminClock")
	Clock adminClock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}
}
