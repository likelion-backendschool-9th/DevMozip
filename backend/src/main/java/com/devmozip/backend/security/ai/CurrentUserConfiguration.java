package com.devmozip.backend.security.ai;

import com.devmozip.backend.repository.AiConversationRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.InsufficientAuthenticationException;

@Configuration(proxyBeanMethods = false)
public class CurrentUserConfiguration {
	@Bean
	@ConditionalOnMissingBean(CurrentUserProvider.class)
	CurrentUserProvider currentUserProvider(Environment environment, AiConversationRepository conversations) {
		boolean development = environment.acceptsProfiles(Profiles.of("(dev | test) & !prod & !production"));
		boolean enabled = environment.getProperty("ai.current-user.development-enabled", Boolean.class, false);
		if (development && enabled) {
			return new DevelopmentCurrentUserProvider(conversations,
				environment.getProperty("ai.current-user.test-user-id", Long.class));
		}
		// Fail closed until the authentication team supplies a real identity adapter.
		return () -> { throw new InsufficientAuthenticationException("AI 사용자 인증이 준비되지 않았습니다."); };
	}
}
