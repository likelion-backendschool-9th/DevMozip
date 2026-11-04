package com.devmozip.backend.security.jwt;

import com.devmozip.backend.config.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class JwtProvider {

    // 토큰 종류 claim. 같은 키로 서명하므로 이 값으로 액세스/리프레시 토큰을 구분한다.
    private static final String TOKEN_TYPE = "type";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final JwtProperties jwtProperties;
    private SecretKey secretKey;

    public JwtProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    public void init() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret이 설정되지 않았습니다. application.properties 또는 JWT_SECRET 환경변수를 확인하세요.");
        }

        // 문자열 상태인 비밀키를 JJWT 규격에 맞는 SecretKey 객체로 변환한다.
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // Access Token 생성
    public String createAccessToken(String username, String role) {
        long now = System.currentTimeMillis();
        long expiration = now + jwtProperties.getAccessTokenExpiration();

        return Jwts.builder()
                .subject(username)                  // 토큰 주인 (사용자 아이디)
                .claim("role", role)                // 사용자 권한 정보 추가
                .claim(TOKEN_TYPE, ACCESS)          // 액세스 토큰임을 표시
                .issuedAt(new java.util.Date(now))  // 토큰 발급 시각
                .expiration(new java.util.Date(expiration)) // 토큰 만료 시각
                .signWith(secretKey)                // 비밀키로 서명
                .compact();
    }

    // Refresh Token 생성
    public String createRefreshToken(String username) {
        long now = System.currentTimeMillis();
        long expiration = now + jwtProperties.getRefreshTokenExpiration();

        return Jwts.builder()
                .subject(username)
                .id(UUID.randomUUID().toString())   // 같은 초에 다시 로그인해도 토큰이 겹치지 않게 한다
                .claim(TOKEN_TYPE, REFRESH)         // 리프레시 토큰임을 표시
                .issuedAt(new java.util.Date(now))
                .expiration(new java.util.Date(expiration))
                .signWith(secretKey)
                .compact();
    }

    // 토큰에서 사용자 아이디(Subject) 추출
    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    // 토큰에서 권한(Role) 정보 추출
    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    // 액세스 토큰 검증: 서명·만료가 유효하고 type이 access인지 (리프레시 토큰은 false)
    public boolean validateAccessToken(String token) {
        return validateToken(token, ACCESS);
    }

    // 리프레시 토큰 검증: 서명·만료가 유효하고 type이 refresh인지 (액세스 토큰은 false)
    public boolean validateRefreshToken(String token) {
        return validateToken(token, REFRESH);
    }

    // 토큰 유효성 검증
    private boolean validateToken(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            return expectedType.equals(claims.get(TOKEN_TYPE, String.class));
        } catch (ExpiredJwtException e) {
            // 토큰이 만료된 경우
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            // 서명이 올바르지 않거나 토큰 형식이 잘못된 경우
            return false;
        }
    }

    // 토큰 내부 데이터(Claims) 파싱 메서드
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey) // 검증에 사용할 비밀키 설정
                .build()
                .parseSignedClaims(token) // 토큰 해석 및 서명 검증
                .getPayload();
    }
}