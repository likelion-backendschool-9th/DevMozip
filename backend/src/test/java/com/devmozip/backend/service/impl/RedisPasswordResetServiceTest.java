package com.devmozip.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.devmozip.backend.config.PasswordResetProperties;
import com.devmozip.backend.service.PasswordResetService;

/**
 * 운영(compose)과 같은 Redis 이미지로 Lua 스크립트와 수명을 실제로 확인한다.
 * Docker를 찾지 못하면 실패 대신 건너뛰므로 그 경우 Redis 검증은 미실행이다.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisPasswordResetServiceTest extends PasswordResetServiceContractTest {

    @Container
    static GenericContainer<?> container = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(container.getHost(), container.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @Override
    protected PasswordResetService createService(PasswordResetProperties properties) {
        redis.delete(keys());
        return new RedisPasswordResetService(redis, properties);
    }

    // 실제로 기다리지 않고 남은 수명을 줄인다. 수명이 없는 키는 영원히 남으므로 여기서 실패시킨다.
    @Override
    protected void advance(Duration duration) {
        for (String key : keys()) {
            long remaining = ttl(key);
            assertThat(remaining).as("수명이 없는 키: %s", key).isPositive();
            if (remaining <= duration.toMillis()) {
                redis.delete(key);
            } else {
                redis.expire(key, remaining - duration.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    @Test
    void 토큰_키는_해시와_이메일만_담고_수명이_있다() {
        createService(properties()).saveToken("member@devmozip.test", "hash");

        assertThat(keys()).containsExactlyInAnyOrder("password-reset:token:hash", "password-reset:member:member@devmozip.test");
        assertThat(redis.opsForValue().get("password-reset:token:hash")).isEqualTo("member@devmozip.test");
        assertThat(redis.opsForValue().get("password-reset:member:member@devmozip.test")).isEqualTo("hash");
        assertRemaining("password-reset:token:hash", TOKEN_TTL);
        assertRemaining("password-reset:member:member@devmozip.test", TOKEN_TTL);
    }

    @Test
    void 소비하거나_지우면_토큰_키가_남지_않는다() {
        PasswordResetService service = createService(properties());
        service.saveToken("member@devmozip.test", "first");
        service.saveToken("member@devmozip.test", "second");
        assertThat(keys()).containsExactlyInAnyOrder("password-reset:token:second", "password-reset:member:member@devmozip.test");

        service.consumeToken("second");
        assertThat(keys()).isEmpty();

        service.saveToken("member@devmozip.test", "third");
        service.removeToken("member@devmozip.test");
        assertThat(keys()).isEmpty();
    }

    @Test
    void 제한_키에_수명이_있다() {
        PasswordResetService service = createService(properties());
        service.tryAcquireEmail("member@devmozip.test");
        service.tryAcquireIp("203.0.113.10");

        assertRemaining("password-reset:cooldown:member@devmozip.test", COOLDOWN);
        assertRemaining("password-reset:count:email:member@devmozip.test", PasswordResetService.RATE_LIMIT_WINDOW);
        assertRemaining("password-reset:count:ip:203.0.113.10", PasswordResetService.RATE_LIMIT_WINDOW);
    }

    private static Set<String> keys() {
        return redis.keys("password-reset:*");
    }

    private static long ttl(String key) {
        return redis.getExpire(key, TimeUnit.MILLISECONDS);
    }

    private static void assertRemaining(String key, Duration expected) {
        assertThat(ttl(key)).isPositive().isLessThanOrEqualTo(expected.toMillis());
        assertThat(ttl(key)).isGreaterThan(expected.minusSeconds(30).toMillis());
    }
}
