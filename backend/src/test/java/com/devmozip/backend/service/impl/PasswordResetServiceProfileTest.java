package com.devmozip.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.devmozip.backend.config.PasswordResetProperties;
import com.devmozip.backend.service.PasswordResetService;

/** 프로필에 따라 구현이 하나만 등록되는지 확인한다. Redis에는 연결하지 않는다. */
class PasswordResetServiceProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(PasswordResetProperties.class)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withUserConfiguration(RedisPasswordResetService.class, InMemoryPasswordResetService.class);

    @Test
    void local_프로필은_메모리_구현을_쓴다() {
        runner.withPropertyValues("spring.profiles.active=local")
                .run(context -> assertThat(context).getBean(PasswordResetService.class).isInstanceOf(InMemoryPasswordResetService.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "test"})
    void 그_외_프로필은_Redis_구현을_쓴다(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> assertThat(context).getBean(PasswordResetService.class).isInstanceOf(RedisPasswordResetService.class));
    }
}
