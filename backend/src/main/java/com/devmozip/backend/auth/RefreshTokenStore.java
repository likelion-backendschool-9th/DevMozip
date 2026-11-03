package com.devmozip.backend.auth;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class RefreshTokenStore {
	private final StringRedisTemplate redis;
	private final JwtProperties properties;

	public RefreshTokenStore(StringRedisTemplate redis, JwtProperties properties) {
		this.redis = redis;
		this.properties = properties;
	}

	public void save(String username, String token) {
		redis.opsForValue().set("refresh:" + username, token, Duration.ofMillis(properties.refreshTokenExpiration()));
	}
	public boolean matches(String username, String token) {
		return token != null && token.equals(redis.opsForValue().get("refresh:" + username));
	}
	public void remove(String username) { redis.delete("refresh:" + username); }
	// 이전 로그인 토큰의 로그아웃이 새 로그인까지 지우지 않도록 비교와 삭제를 원자적으로 처리한다.
	public void removeMatching(String username, String token) {
		redis.execute(new DefaultRedisScript<Long>(
			"if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class),
			List.of("refresh:" + username), token);
	}
}
