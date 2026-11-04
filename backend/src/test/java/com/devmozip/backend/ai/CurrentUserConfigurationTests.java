package com.devmozip.backend.ai;

import com.devmozip.backend.security.ai.CurrentUserProvider;

import com.devmozip.backend.security.ai.CurrentUserConfiguration;

import com.devmozip.backend.repository.AiConversationRepository;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentUserConfigurationTests {
	private final AiConversationRepository conversations = mock(AiConversationRepository.class);
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(CurrentUserConfiguration.class).withBean(AiConversationRepository.class, () -> conversations);

	@Test void defaultAndProductionRejectEvenWithOptIn() {
		for (String profiles : new String[]{"", "prod", "dev,prod", "test,prod", "dev,production", "test,production"}) {
			runner.withPropertyValues("spring.profiles.active=" + profiles,
				"ai.current-user.development-enabled=true", "ai.current-user.test-user-id=101")
				.run(context -> assertThrows(InsufficientAuthenticationException.class,
					() -> context.getBean(CurrentUserProvider.class).getCurrentUserId()));
		}
		verifyNoInteractions(conversations);
	}
	@Test void devAndTestRequireOptInAndAnExistingConfiguredUser() {
		when(conversations.existsUserById(101)).thenReturn(true);
		for (String profile : new String[]{"dev", "test"}) {
			runner.withPropertyValues("spring.profiles.active=" + profile,
				"ai.current-user.development-enabled=true", "ai.current-user.test-user-id=101")
				.run(context -> assertEquals(101, context.getBean(CurrentUserProvider.class).getCurrentUserId()));
		}
		runner.withPropertyValues("spring.profiles.active=dev", "ai.current-user.test-user-id=101")
			.run(context -> assertThrows(InsufficientAuthenticationException.class,
				() -> context.getBean(CurrentUserProvider.class).getCurrentUserId()));
		for (String id : new String[]{"", "0", "-1", "999"}) {
			runner.withPropertyValues("spring.profiles.active=test", "ai.current-user.development-enabled=true",
				"ai.current-user.test-user-id=" + id)
				.run(context -> assertThrows(InsufficientAuthenticationException.class,
					() -> context.getBean(CurrentUserProvider.class).getCurrentUserId()));
		}
	}
	@Test void suppliedRealAdapterReplacesTheFallback() {
		CurrentUserProvider adapter = () -> 202;
		runner.withBean(CurrentUserProvider.class, () -> adapter)
			.run(context -> assertSame(adapter, context.getBean(CurrentUserProvider.class)));
	}
}
