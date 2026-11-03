package com.devmozip.backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
	private final JwtProvider jwt;
	public JwtAuthenticationFilter(JwtProvider jwt) { this.jwt = jwt; }
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			String token = header.substring(7);
			try {
				if (jwt.validateToken(token)) {
					String role = jwt.getRole(token);
					if ("USER".equals(role) || "ADMIN".equals(role)) {
						var authorities = "ADMIN".equals(role)
							? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
							: List.of(new SimpleGrantedAuthority("ROLE_USER"));
						SecurityContextHolder.getContext().setAuthentication(
							new UsernamePasswordAuthenticationToken(jwt.getUsername(token), null, authorities));
					}
				}
			} catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
				SecurityContextHolder.clearContext();
			}
		}
		chain.doFilter(request, response);
	}
}
