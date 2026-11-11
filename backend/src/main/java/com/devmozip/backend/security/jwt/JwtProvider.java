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
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.service.AccountWithdrawalTasks;

@Component
public class JwtProvider {

    // 토큰 종류 claim. 같은 키로 서명하므로 이 값으로 액세스/리프레시 토큰을 구분한다.
    private static final String TOKEN_TYPE = "type";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";
    private static final String KAKAO_SIGNUP = "kakao_signup";

    // 카카오 인증을 마친 뒤 가입 폼을 작성할 시간 (30분)
    private static final long KAKAO_SIGNUP_TOKEN_EXPIRATION = 30 * 60 * 1000L;

    private final JwtProperties jwtProperties;
    private SecretKey secretKey;
    private final ObjectProvider<MemberRepository> members;
    private final ObjectProvider<AccountWithdrawalTasks> withdrawals;

    public JwtProvider(JwtProperties jwtProperties) {
        this(jwtProperties, null, null);
    }

    @Autowired
    public JwtProvider(JwtProperties jwtProperties, ObjectProvider<MemberRepository> members, ObjectProvider<AccountWithdrawalTasks> withdrawals) {
        this.jwtProperties = jwtProperties;
        this.members = members;
        this.withdrawals = withdrawals;
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
                .claim("accountId", accountId(username)) // 같은 이메일 재가입 시 이전 계정의 토큰을 구분한다
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
                .claim("accountId", accountId(username))
                .id(UUID.randomUUID().toString())   // 같은 초에 다시 로그인해도 토큰이 겹치지 않게 한다
                .claim(TOKEN_TYPE, REFRESH)         // 리프레시 토큰임을 표시
                .issuedAt(new java.util.Date(now))
                .expiration(new java.util.Date(expiration))
                .signWith(secretKey)
                .compact();
    }

    // 카카오 가입 토큰 생성: 가입 폼을 제출할 때 카카오 회원번호를 위조 없이 확인하기 위해 쓴다
    public String createKakaoSignupToken(String kakaoId) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .subject(kakaoId)                   // 카카오 회원번호
                .claim(TOKEN_TYPE, KAKAO_SIGNUP)    // 가입에만 쓰는 토큰임을 표시
                .issuedAt(new java.util.Date(now))
                .expiration(new java.util.Date(now + KAKAO_SIGNUP_TOKEN_EXPIRATION))
                .signWith(secretKey)
                .compact();
    }

    // 토큰에서 사용자 아이디(Subject) 추출
    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    // 카카오 가입 토큰에서 카카오 회원번호 추출
    public String getKakaoId(String token) {
        return parseClaims(token).getSubject();
    }

    // 토큰에서 권한(Role) 정보 추출
    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    // 토큰 만료 시각 (메신저 WebSocket 연결을 이 시각에 닫는다)
    public Instant getExpiration(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    // 액세스 토큰 검증: 서명·만료가 유효하고 type이 access인지 (리프레시 토큰은 false)
    public boolean validateAccessToken(String token) {
        if (!validateToken(token, ACCESS)) return false;
        return matchesAccount(token);
    }

    private Long accountId(String username) {
        MemberRepository repository = members == null ? null : members.getIfAvailable();
        return repository == null ? null : repository.findByEmail(username).map(Member::getId).orElse(null);
    }

    private boolean matchesAccount(String token) {
        Claims claims = parseClaims(token);
        Long accountId = claims.get("accountId", Long.class);
        MemberRepository repository = members == null ? null : members.getIfAvailable();
        if (accountId != null && repository != null) {
            return repository.findByEmail(claims.getSubject())
                    .filter(member -> accountId.equals(member.getId())).isPresent();
        }
        AccountWithdrawalTasks tasks = withdrawals == null ? null : withdrawals.getIfAvailable();
        return tasks == null || !tasks.legacyRevoked(claims.getSubject());
    }

    // 리프레시 토큰 검증: 서명·만료가 유효하고 type이 refresh인지 (액세스 토큰은 false)
    public boolean validateRefreshToken(String token) {
        return validateToken(token, REFRESH) && matchesAccount(token);
    }

    // 카카오 가입 토큰 검증: 서명·만료가 유효하고 type이 kakao_signup인지
    public boolean validateKakaoSignupToken(String token) {
        return validateToken(token, KAKAO_SIGNUP);
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
