package com.devmozip.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {
	private final JwtProperties properties;
	private final SecretKey key;

	public JwtProvider(JwtProperties properties) {
		this.properties = properties;
		this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
	}

	public String createAccessToken(String username, String role) {
		return Jwts.builder().subject(username).claim("role", role).issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + properties.accessTokenExpiration()))
			.signWith(key, Jwts.SIG.HS256).compact();
	}

	public String createRefreshToken(String username) {
		return Jwts.builder().subject(username).id(UUID.randomUUID().toString()).issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + properties.refreshTokenExpiration()))
			.signWith(key, Jwts.SIG.HS256).compact();
	}

	private Claims claims(String token) {
		return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
	}

	public String getUsername(String token) { return claims(token).getSubject(); }
	public String getRole(String token) { return claims(token).get("role", String.class); }
	public boolean validateToken(String token) {
		try {
			Claims claims = claims(token);
			return claims.getSubject() != null && claims.getExpiration() != null;
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}
}
