package com.devmozip.backend.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiUsageConfiguration {
	@Bean("aiUsageClock")
	Clock aiUsageClock() { return Clock.systemUTC(); }
}
