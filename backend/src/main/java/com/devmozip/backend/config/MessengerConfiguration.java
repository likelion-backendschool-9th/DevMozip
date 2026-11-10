package com.devmozip.backend.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MessengerConfiguration {
	/** 시각 컬럼에는 시간대가 저장되지 않고 컨테이너 시간대는 UTC다. 저장과 응답의 기준을 여기로 고정한다. */
	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Bean("messengerClock")
	Clock messengerClock() {
		return Clock.system(ZONE);
	}
}
