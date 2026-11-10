package com.devmozip.backend.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** SNS 피드 (docs/feed-plan.md). DATETIME 컬럼과 이미지 파일 이름은 한국 시각으로 남긴다. */
@Configuration(proxyBeanMethods = false)
public class FeedConfiguration {
	@Bean("feedClock")
	Clock feedClock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}
}
