package com.devmozip.backend.config;

import java.util.Set;
import com.devmozip.backend.service.ProjectApplicationUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 현재 프로젝트 지원 도메인은 미구현이다. #48에서 실제 조회 어댑터로 교체한다. */
@Configuration(proxyBeanMethods = false)
public class ProjectApplicationUsageConfiguration {
	@Bean
	@ConditionalOnMissingBean(ProjectApplicationUsage.class)
	ProjectApplicationUsage projectApplicationUsage() { return projectId -> Set.of(); }
}
