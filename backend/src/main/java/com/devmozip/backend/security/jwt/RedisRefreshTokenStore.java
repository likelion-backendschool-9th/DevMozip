package com.devmozip.backend.security.jwt;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

import com.devmozip.backend.config.JwtProperties;

/**
 * 서버용 저장소. 블루그린의 두 백엔드가 같은 Redis를 쓰므로 재시작·색 전환 후에도 로그인이 유지된다.
 * 키는 "refresh:{사용자 아이디}"이고, 리프레시 토큰 수명(14일)이 지나면 Redis가 스스로 지운다.
 */
@Component
@Profile("!local")
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";

    // 비교와 삭제 사이에 새 로그인이 끼어들지 않도록 Redis 안에서 한 번에 실행한다
    private static final RedisScript<Long> REMOVE_MATCHING = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final JwtProperties jwtProperties;

    public RedisRefreshTokenStore(StringRedisTemplate redis, JwtProperties jwtProperties) {
        this.redis = redis;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public void save(String username, String refreshToken) {
        redis.opsForValue().set(key(username), refreshToken,
                Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()));
    }

    @Override
    public boolean matches(String username, String refreshToken) {
        return refreshToken != null && refreshToken.equals(redis.opsForValue().get(key(username)));
    }

    @Override
    public void remove(String username) {
        redis.delete(key(username));
    }

    @Override
    public void removeMatching(String username, String refreshToken) {
        redis.execute(REMOVE_MATCHING, List.of(key(username)), refreshToken);
    }

    private static String key(String username) {
        return KEY_PREFIX + username;
    }
}
