package com.devmozip.backend.project;

import com.devmozip.backend.security.project.ProjectConfiguration;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.junit.jupiter.api.Assertions.*;

class ProjectConfigurationTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(ProjectConfiguration.class);
	@Test void requiresExplicitDevelopmentOptInAndPositiveUserId() {
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
}
