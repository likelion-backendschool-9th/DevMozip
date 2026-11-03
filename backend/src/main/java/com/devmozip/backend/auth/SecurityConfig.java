package com.devmozip.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwt,
		JsonAuthenticationEntryPoint entryPoint, JsonAccessDeniedHandler deniedHandler) throws Exception {
		return http.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/error").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
				.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.anyRequest().authenticated())
			.exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
			.addFilterBefore(new JwtAuthenticationFilter(jwt), UsernamePasswordAuthenticationFilter.class)
			.build();
	}
	@Bean
	PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}
	@Bean
	UserDetailsService userDetailsService(PasswordEncoder encoder,
		@Value("${auth.bootstrap.user-username}") String username,
		@Value("${auth.bootstrap.user-password}") String password,
		@Value("${auth.bootstrap.admin-username}") String adminUsername,
		@Value("${auth.bootstrap.admin-password}") String adminPassword) {
		if (password.isBlank() || adminPassword.isBlank() || username.equals(adminUsername)) {
			throw new IllegalArgumentException("임시 인증 계정 설정을 확인해 주세요.");
		}
		return new InMemoryUserDetailsManager(
			User.withUsername(username).password(encoder.encode(password)).roles("USER").build(),
			User.withUsername(adminUsername).password(encoder.encode(adminPassword)).roles("USER", "ADMIN").build());
	}
}
