package com.devmozip.backend.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.devmozip.backend.config.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** 카카오 가입 토큰이 가입에만 쓰이고 로그인 토큰과 섞이지 않는지 확인한다. */
class JwtProviderTest {

    private static final String SECRET = "test-only-secret-key-at-least-32-bytes!!";
    private static final String KAKAO_ID = "123456789";

    private JwtProvider jwtProvider;

    private static JwtProvider provider(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setAccessTokenExpiration(60_000);
        properties.setRefreshTokenExpiration(60_000);
        JwtProvider provider = new JwtProvider(properties);
        provider.init();
        return provider;
    }

    private static SecretKey key() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @BeforeEach
    void setUp() {
        jwtProvider = provider(SECRET);
    }

    @Test
    void 가입_토큰에서_카카오_회원번호를_꺼낸다() {
        String token = jwtProvider.createKakaoSignupToken(KAKAO_ID);

        assertThat(jwtProvider.validateKakaoSignupToken(token)).isTrue();
        assertThat(jwtProvider.getKakaoId(token)).isEqualTo(KAKAO_ID);
    }

    @Test
    void 가입_토큰은_30분_동안_유효하다() {
        String token = jwtProvider.createKakaoSignupToken(KAKAO_ID);

        Claims claims = Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();

        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(30 * 60 * 1000L);
    }

    @Test
    void 가입_토큰은_액세스_토큰이나_리프레시_토큰으로_쓸_수_없다() {
        String token = jwtProvider.createKakaoSignupToken(KAKAO_ID);

        assertThat(jwtProvider.validateAccessToken(token)).isFalse();
        assertThat(jwtProvider.validateRefreshToken(token)).isFalse();
    }

    @Test
    void 액세스_토큰이나_리프레시_토큰은_가입_토큰으로_쓸_수_없다() {
        assertThat(jwtProvider.validateKakaoSignupToken(jwtProvider.createAccessToken(KAKAO_ID, "USER"))).isFalse();
        assertThat(jwtProvider.validateKakaoSignupToken(jwtProvider.createRefreshToken(KAKAO_ID))).isFalse();
    }

    @Test
    void 위조되거나_다른_키로_서명한_가입_토큰은_거절한다() {
        String token = jwtProvider.createKakaoSignupToken(KAKAO_ID);
        String forgedPayload = Jwts.builder()
                .subject("987654321")
                .claim("type", "kakao_signup")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key())
                .compact()
                .split("\\.")[1];
        String[] parts = token.split("\\.");

        assertThat(jwtProvider.validateKakaoSignupToken(parts[0] + "." + forgedPayload + "." + parts[2])).isFalse();
        assertThat(provider(SECRET + "-other").validateKakaoSignupToken(token)).isFalse();
        assertThat(jwtProvider.validateKakaoSignupToken("not-a-token")).isFalse();
        assertThat(jwtProvider.validateKakaoSignupToken(null)).isFalse();
    }

    @Test
    void 만료된_가입_토큰은_거절한다() {
        String expired = Jwts.builder()
                .subject(KAKAO_ID)
                .claim("type", "kakao_signup")
                .expiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(key())
                .compact();

        assertThat(jwtProvider.validateKakaoSignupToken(expired)).isFalse();
    }
}
