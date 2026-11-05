package com.devmozip.backend.security.ai;

import com.devmozip.backend.repository.AiConversationRepository;
import com.devmozip.backend.repository.MemberRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
public class CurrentUserConfiguration {
	@Bean
	@ConditionalOnMissingBean(CurrentUserProvider.class)
	CurrentUserProvider currentUserProvider(Environment environment, AiConversationRepository conversations,
		MemberRepository members) {
		boolean development = environment.acceptsProfiles(Profiles.of("(dev | test) & !prod & !production"));
		boolean enabled = environment.getProperty("ai.current-user.development-enabled", Boolean.class, false);
		if (development && enabled) {
			return new DevelopmentCurrentUserProvider(conversations,
				environment.getProperty("ai.current-user.test-user-id", Long.class));
		}
		return new AuthenticatedCurrentUserProvider(members);
	}
}
