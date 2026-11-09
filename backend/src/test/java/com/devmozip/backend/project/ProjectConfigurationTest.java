package com.devmozip.backend.project;

import com.devmozip.backend.security.project.ProjectConfiguration;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import com.devmozip.backend.repository.MemberRepository;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.junit.jupiter.api.Assertions.*;

class ProjectConfigurationTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(ProjectConfiguration.class).withBean(MemberRepository.class, () -> mock(MemberRepository.class));
	@Test void defaultsToAuthenticationAndRejectsInvalidDevelopmentOverrides() {
		for (String[] properties : new String[][] {
			{}, {"spring.profiles.active=test"},
			{"spring.profiles.active=test", "project.current-user.development-enabled=true", "project.current-user.test-user-id=0"},
			{"spring.profiles.active=prod,test", "project.current-user.development-enabled=true", "project.current-user.test-user-id=101"},
			{"spring.profiles.active=production,local", "project.current-user.development-enabled=true", "project.current-user.test-user-id=101"}
		}) {
			runner.withPropertyValues(properties).run(context -> assertThrows(InsufficientAuthenticationException.class,
				() -> context.getBean(ProjectCurrentUserProvider.class).getCurrentUserId()));
		}
	}
	@Test void permitsFixedIdOnlyInDevelopmentProfiles() {
		for (String profile : new String[]{"local", "dev", "test"}) {
			runner.withPropertyValues("spring.profiles.active=" + profile, "project.current-user.development-enabled=true",
				"project.current-user.test-user-id=101").run(context ->
				assertEquals(101L, context.getBean(ProjectCurrentUserProvider.class).getCurrentUserId()));
		}
	}
	@Test void productionUsesRealMemberEvenWithDevelopmentFlag() {
		MemberRepository members=mock(MemberRepository.class);
		com.devmozip.backend.entity.Member member=mock(com.devmozip.backend.entity.Member.class);
		org.mockito.Mockito.when(member.getId()).thenReturn(5L);
		org.mockito.Mockito.when(member.getStatus()).thenReturn(com.devmozip.backend.entity.Member.MemberStatus.ACTIVE);
		org.mockito.Mockito.when(members.findByEmail("premium@devmozip.demo")).thenReturn(java.util.Optional.of(member));
		var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("premium@devmozip.demo",null,
			java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PREMIUM")));
		org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
		try {
			new ApplicationContextRunner().withUserConfiguration(ProjectConfiguration.class).withBean(MemberRepository.class,()->members).withPropertyValues("spring.profiles.active=prod,local",
				"project.current-user.development-enabled=true","project.current-user.test-user-id=999")
				.run(context->assertEquals(5L,context.getBean(ProjectCurrentUserProvider.class).getCurrentUserId()));
		} finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
	}

}
