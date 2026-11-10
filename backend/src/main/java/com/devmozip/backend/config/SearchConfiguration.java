package com.devmozip.backend.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 통합 검색(WBS 6.x). 프로젝트 모집 상태의 오늘 날짜는 프로젝트 기능과 같은 Asia/Seoul 기준이다. */
@Configuration(proxyBeanMethods = false)
public class SearchConfiguration {
	@Bean("searchClock")
	Clock searchClock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}
}
