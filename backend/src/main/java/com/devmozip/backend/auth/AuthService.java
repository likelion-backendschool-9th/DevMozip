package com.devmozip.backend.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
	private final AuthenticationManager manager;
	private final UserDetailsService users;
	private final JwtProvider jwt;
	private final RefreshTokenStore store;
	public AuthService(AuthenticationManager manager, UserDetailsService users, JwtProvider jwt, RefreshTokenStore store) {
		this.manager = manager; this.users = users; this.jwt = jwt; this.store = store;
	}
	private String role(UserDetails user) {
		return user.getAuthorities().stream().map(GrantedAuthority::getAuthority)
			.anyMatch("ROLE_ADMIN"::equals) ? "ADMIN" : "USER";
	}
	public LoginResponse login(LoginRequest request) {
		if (request.username() == null || request.password() == null) {
			throw new org.springframework.security.authentication.BadCredentialsException("인증 실패");
		}
		var authentication = manager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
		UserDetails user = users.loadUserByUsername(authentication.getName());
		String refresh = jwt.createRefreshToken(user.getUsername());
		store.save(user.getUsername(), refresh);
		return new LoginResponse(jwt.createAccessToken(user.getUsername(), role(user)), refresh);
	}
	public LoginResponse refresh(String refresh) {
		if (!jwt.validateToken(refresh) || jwt.getRole(refresh) != null) throw new InvalidTokenException();
		String username = jwt.getUsername(refresh);
		if (!store.matches(username, refresh)) throw new InvalidTokenException();
		UserDetails user;
		try { user = users.loadUserByUsername(username); }
		catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) { throw new InvalidTokenException(); }
		if (!user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired() || !user.isCredentialsNonExpired()) throw new InvalidTokenException();
		return new LoginResponse(jwt.createAccessToken(username, role(user)), refresh);
	}
	public void logout(String refresh) {
		if (jwt.validateToken(refresh) && jwt.getRole(refresh) == null) store.removeMatching(jwt.getUsername(refresh), refresh);
	}
}
