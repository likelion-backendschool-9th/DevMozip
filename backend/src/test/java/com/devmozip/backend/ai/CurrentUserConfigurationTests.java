package com.devmozip.backend.ai;

import com.devmozip.backend.security.ai.CurrentUserProvider;

import com.devmozip.backend.security.ai.CurrentUserConfiguration;

import com.devmozip.backend.repository.AiConversationRepository;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.entity.Member;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentUserConfigurationTests {
	private final AiConversationRepository conversations = mock(AiConversationRepository.class);
	private final MemberRepository members = mock(MemberRepository.class);
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(CurrentUserConfiguration.class).withBean(AiConversationRepository.class, () -> conversations)
		.withBean(MemberRepository.class, () -> members);

	@AfterEach void clearAuthentication() {
		SecurityContextHolder.clearContext();
	}

	@Test void productionUsesAuthenticatedEmailInsteadOfConfiguredDevelopmentId() {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(202L);
		when(members.findByEmail("member@ai.test")).thenReturn(Optional.of(member));
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
			"member@ai.test", null, AuthorityUtils.createAuthorityList("ROLE_USER")));
		for (String profile : new String[]{"", "local", "prod", "dev,production"}) {
			runner.withPropertyValues("spring.profiles.active=" + profile,
				"ai.current-user.development-enabled=true", "ai.current-user.test-user-id=101")
				.run(context -> assertEquals(202, context.getBean(CurrentUserProvider.class).getCurrentUserId()));
		}
		verifyNoInteractions(conversations);
	}

	@Test void anonymousAndUnauthenticatedIdentitiesNeverQueryUsers() {
		for (var authentication : java.util.List.of(
			new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")),
			new UsernamePasswordAuthenticationToken("member@ai.test", null))) {
			SecurityContextHolder.getContext().setAuthentication(authentication);
			runner.run(context -> assertThrows(InsufficientAuthenticationException.class,
				() -> context.getBean(CurrentUserProvider.class).getCurrentUserId()));
		}
		verifyNoInteractions(members, conversations);
	}

	@Test void defaultAndProductionRequireAuthenticationEvenWithDevelopmentOptIn() {
		for (String profiles : new String[]{"", "prod", "dev,prod", "test,prod", "dev,production", "test,production"}) {
			runner.withPropertyValues("spring.profiles.active=" + profiles,
				"ai.current-user.development-enabled=true", "ai.current-user.test-user-id=101")
				.run(context -> assertThrows(InsufficientAuthenticationException.class,
					() -> context.getBean(CurrentUserProvider.class).getCurrentUserId()));
		}
		verifyNoInteractions(conversations, members);
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
