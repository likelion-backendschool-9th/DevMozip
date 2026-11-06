package com.devmozip.backend.security.project;

import java.time.Clock;
import java.time.ZoneId;
import com.devmozip.backend.repository.MemberRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.InsufficientAuthenticationException;

@Configuration(proxyBeanMethods = false)
public class ProjectConfiguration {
	@Bean("projectClock")
	Clock projectClock() { return Clock.system(ZoneId.of("Asia/Seoul")); }

	@Bean
	@ConditionalOnMissingBean(ProjectCurrentUserProvider.class)
	ProjectCurrentUserProvider projectCurrentUserProvider(Environment environment, MemberRepository members) {
		boolean development = environment.acceptsProfiles(Profiles.of("(local | dev | test) & !prod & !production"));
		boolean enabled = environment.getProperty("project.current-user.development-enabled", Boolean.class, false);
		if (!development || !enabled) return new AuthenticatedProjectCurrentUserProvider(members);
		Long id = environment.getProperty("project.current-user.test-user-id", Long.class);
		return () -> {
			if (id == null || id <= 0) {
				throw new InsufficientAuthenticationException("프로젝트 개발용 사용자 설정을 확인해 주세요.");
			}
			return id;
		};
	}
}
