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

import com.devmozip.backend.config.PaymentProperties;
import com.devmozip.backend.entity.enums.PaymentProvider;
import com.devmozip.backend.service.PaymentCheckoutService;
import com.devmozip.backend.service.PaymentCheckoutService.PendingCheckout;

import tools.jackson.databind.json.JsonMapper;

/**
 * 운영(compose)과 같은 Redis 이미지로 수명과 GETDEL을 실제로 확인한다.
 * Docker를 찾지 못하면 실패 대신 건너뛰므로 그 경우 Redis 검증은 미실행이다.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisPaymentCheckoutServiceTest extends PaymentCheckoutServiceContractTest {

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

	private static Set<String> keys() {
		return redis.keys("payment-checkout:*");
	}

	@Override
	protected PaymentCheckoutService createService(PaymentProperties properties) {
		Set<String> keys = keys();
		if (!keys.isEmpty()) redis.delete(keys);
		return new RedisPaymentCheckoutService(redis, properties, JsonMapper.builder().build());
	}

	// 실제로 기다리지 않고 남은 수명을 줄인다. 수명이 없는 키는 영원히 남으므로 여기서 실패시킨다.
	@Override
	protected void advance(Duration duration) {
		for (String key : keys()) {
			long remaining = redis.getExpire(key, TimeUnit.MILLISECONDS);
			assertThat(remaining).as("수명이 없는 키: %s", key).isPositive();
			if (remaining <= duration.toMillis()) redis.delete(key);
			else redis.expire(key, Duration.ofMillis(remaining - duration.toMillis()));
		}
	}

	@Test
	void 저장값에_금액과_구매자_ID를_담고_15분_수명을_둔다() {
		service.save(new PendingCheckout("order-ttl", 7L, 1L, PaymentProvider.KAKAO_PAY, 9900, "KRW", "customer-x"));
		long remaining = redis.getExpire("payment-checkout:order-ttl", TimeUnit.SECONDS);
		assertThat(remaining).isBetween(14 * 60L, 15 * 60L);
		assertThat(redis.opsForValue().get("payment-checkout:order-ttl")).contains("\"amount\":9900").contains("customer-x");
	}
}
