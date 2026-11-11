package com.devmozip.backend.service;

import java.util.List;
import com.devmozip.backend.config.JwtProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class AccountWithdrawalTasksRedisTest {
	@Container static GenericContainer<?> container = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
	static LettuceConnectionFactory factory;
	static StringRedisTemplate redis;
	AccountWithdrawalTasks first;
	AccountWithdrawalTasks second;

	@BeforeAll static void connect() {
		factory = new LettuceConnectionFactory(container.getHost(), container.getMappedPort(6379));
		factory.afterPropertiesSet(); factory.start();
		redis = new StringRedisTemplate(factory);
	}
	@AfterAll static void close() { if (factory != null) factory.destroy(); }
	@BeforeEach void setup() {
		try (var connection = factory.getConnection()) { connection.serverCommands().flushDb(); }
		var properties = new JwtProperties(); properties.setAccessTokenExpiration(60_000);
		first = new AccountWithdrawalTasks(redis, JsonMapper.builder().build(), properties, new MockEnvironment());
		second = new AccountWithdrawalTasks(redis, JsonMapper.builder().build(), properties, new MockEnvironment());
	}

	@Test void pendingJobSurvivesStoreRecreationAndIsOnlyRemovedByMatchingOperation() {
		var original = new AccountWithdrawalTasks.Job(1, "encrypted-key", List.of("photo.png"));
		first.save(original);
		assertThat(second.read("withdrawal:cleanup:1")).isEqualTo(original);
		assertThat(second.keys()).containsExactly("withdrawal:cleanup:1");
		var replacement = new AccountWithdrawalTasks.Job(1, "new-encrypted-key", List.of());
		second.save(replacement);
		first.removePrepared(original);
		assertThat(second.read("withdrawal:cleanup:1")).isEqualTo(replacement);
		second.removePrepared(replacement);
		assertThat(first.keys()).isEmpty();
	}

	@Test void leaseIsSharedAndCannotBeReleasedByAnotherWorker() {
		String key = "withdrawal:cleanup:1";
		String lease = first.acquire(key);
		assertThat(lease).isNotNull();
		assertThat(second.acquire(key)).isNull();
		second.release(key, "different-worker");
		assertThat(second.acquire(key)).isNull();
		first.release(key, lease);
		assertThat(second.acquire(key)).isNotNull();
	}

	@Test void legacyRevocationIsSharedExpiresAndDoesNotStoreEmail() {
		first.revokeLegacy("withdraw@example.test");
		assertThat(second.legacyRevoked("withdraw@example.test")).isTrue();
		assertThat(second.legacyRevoked("other@example.test")).isFalse();
		var keys = redis.keys("withdrawal:legacy:*");
		assertThat(keys).hasSize(1).allMatch(key -> !key.contains("withdraw@example.test"));
		assertThat(redis.getExpire(keys.iterator().next())).isBetween(1L, 120L);
	}

	@Test void scansAllPendingJobsBeyondOneRedisBatch() {
		for (long id = 1; id <= 150; id++) first.save(new AccountWithdrawalTasks.Job(id, null, List.of()));
		assertThat(second.keys()).hasSize(150);
	}
}
