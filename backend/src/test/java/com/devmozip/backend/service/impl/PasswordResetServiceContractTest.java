package com.devmozip.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.devmozip.backend.config.PasswordResetProperties;
import com.devmozip.backend.service.PasswordResetService;

/** Redis 구현과 메모리 구현이 같은 규칙을 지키는지 같은 시나리오로 확인한다. */
abstract class PasswordResetServiceContractTest {

    protected static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    protected static final Duration COOLDOWN = Duration.ofSeconds(60);
    private static final int EMAIL_LIMIT = 5;
    private static final int IP_LIMIT = 20;
    private static final String EMAIL = "member@devmozip.test";
    private static final String OTHER_EMAIL = "other@devmozip.test";
    private static final String IP = "203.0.113.10";

    private PasswordResetService service;

    /** 비어 있는 저장소를 만든다. */
    protected abstract PasswordResetService createService(PasswordResetProperties properties);

    /** 저장된 값의 수명을 그만큼 흘려보낸다. */
    protected abstract void advance(Duration duration);

    protected static PasswordResetProperties properties() {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setTokenTtl(TOKEN_TTL);
        properties.setEmailCooldown(COOLDOWN);
        properties.setEmailHourlyLimit(EMAIL_LIMIT);
        properties.setIpHourlyLimit(IP_LIMIT);
        return properties;
    }

    @BeforeEach
    void setUp() {
        service = createService(properties());
    }

    @Test
    void 저장한_토큰을_소비하면_이메일을_돌려주고_다시는_쓸_수_없다() {
        service.saveToken(EMAIL, "hash");

        assertThat(service.consumeToken("hash")).contains(EMAIL);
        assertThat(service.consumeToken("hash")).isEmpty();
    }

    @Test
    void 저장하지_않은_토큰은_소비할_수_없다() {
        assertThat(service.consumeToken("unknown")).isEmpty();
    }

    @Test
    void 토큰은_수명이_지나면_쓸_수_없다() {
        service.saveToken(EMAIL, "alive");
        advance(TOKEN_TTL.minusMinutes(1));
        assertThat(service.consumeToken("alive")).contains(EMAIL);

        service.saveToken(EMAIL, "expired");
        advance(TOKEN_TTL);
        assertThat(service.consumeToken("expired")).isEmpty();
    }

    @Test
    void 새_토큰이_이전_토큰을_무효로_만든다() {
        service.saveToken(EMAIL, "first");
        service.saveToken(EMAIL, "second");

        assertThat(service.consumeToken("first")).isEmpty();
        assertThat(service.consumeToken("second")).contains(EMAIL);
    }

    @Test
    void 새_토큰은_다른_회원의_토큰을_건드리지_않는다() {
        service.saveToken(EMAIL, "mine");
        service.saveToken(OTHER_EMAIL, "other");

        assertThat(service.consumeToken("mine")).contains(EMAIL);
        assertThat(service.consumeToken("other")).contains(OTHER_EMAIL);
    }

    @Test
    void 소비한_뒤_새_토큰을_저장할_수_있다() {
        service.saveToken(EMAIL, "first");
        service.consumeToken("first");
        service.saveToken(EMAIL, "second");

        assertThat(service.consumeToken("second")).contains(EMAIL);
    }

    @Test
    void 회원의_토큰을_지우면_쓸_수_없고_다른_회원은_영향이_없다() {
        service.saveToken(EMAIL, "mine");
        service.saveToken(OTHER_EMAIL, "other");

        service.removeToken(EMAIL);
        service.removeToken("none@devmozip.test");

        assertThat(service.consumeToken("mine")).isEmpty();
        assertThat(service.consumeToken("other")).contains(OTHER_EMAIL);
    }

    @Test
    void 같은_토큰을_동시에_소비하면_한_요청만_성공한다() throws Exception {
        service.saveToken(EMAIL, "hash");

        List<Optional<String>> results = concurrently(32, () -> service.consumeToken("hash"));

        assertThat(results.stream().filter(Optional::isPresent)).hasSize(1);
    }

    @Test
    void 같은_이메일은_재요청_간격이_지나야_다시_요청할_수_있다() {
        assertThat(service.tryAcquireEmail(EMAIL)).isTrue();
        assertThat(service.tryAcquireEmail(EMAIL)).isFalse();

        advance(COOLDOWN.minusSeconds(10));
        assertThat(service.tryAcquireEmail(EMAIL)).isFalse();

        advance(Duration.ofSeconds(10));
        assertThat(service.tryAcquireEmail(EMAIL)).isTrue();
    }

    @Test
    void 같은_이메일은_시간당_횟수를_넘으면_구간이_끝날_때까지_요청할_수_없다() {
        for (int i = 0; i < EMAIL_LIMIT; i++) {
            assertThat(service.tryAcquireEmail(EMAIL)).isTrue();
            advance(COOLDOWN);
        }
        assertThat(service.tryAcquireEmail(EMAIL)).isFalse();

        // 첫 요청부터 구간을 재므로 지금까지 흐른 시간을 뺀 나머지만 기다리면 풀린다
        advance(PasswordResetService.RATE_LIMIT_WINDOW.minus(COOLDOWN.multipliedBy(EMAIL_LIMIT)));
        assertThat(service.tryAcquireEmail(EMAIL)).isTrue();
    }

    @Test
    void 재요청_간격에_걸린_요청은_시간당_횟수에_세지_않는다() {
        for (int i = 0; i < EMAIL_LIMIT; i++) {
            assertThat(service.tryAcquireEmail(EMAIL)).isTrue();
            assertThat(service.tryAcquireEmail(EMAIL)).isFalse();
            assertThat(service.tryAcquireEmail(EMAIL)).isFalse();
            advance(COOLDOWN);
        }
    }

    @Test
    void 이메일_제한은_이메일별로_따로_센다() {
        assertThat(service.tryAcquireEmail(EMAIL)).isTrue();
        assertThat(service.tryAcquireEmail(OTHER_EMAIL)).isTrue();
    }

    @Test
    void 같은_이메일의_동시_요청은_하나만_통과한다() throws Exception {
        List<Boolean> results = concurrently(32, () -> service.tryAcquireEmail(EMAIL));

        assertThat(results.stream().filter(Boolean::booleanValue)).hasSize(1);
    }

    @Test
    void 같은_IP는_시간당_횟수를_넘으면_구간이_끝날_때까지_요청할_수_없다() {
        for (int i = 0; i < IP_LIMIT; i++) {
            assertThat(service.tryAcquireIp(IP)).isTrue();
        }
        assertThat(service.tryAcquireIp(IP)).isFalse();

        // 거절된 요청이 구간을 늘리지 않으므로 처음부터 잰 시간만큼 지나면 풀린다
        advance(PasswordResetService.RATE_LIMIT_WINDOW.minusMinutes(1));
        assertThat(service.tryAcquireIp(IP)).isFalse();

        advance(Duration.ofMinutes(1));
        assertThat(service.tryAcquireIp(IP)).isTrue();
    }

    @Test
    void IP_제한은_IP별로_따로_세고_이메일_제한과_섞이지_않는다() {
        for (int i = 0; i < IP_LIMIT; i++) {
            service.tryAcquireIp(IP);
        }

        assertThat(service.tryAcquireIp("203.0.113.11")).isTrue();
        assertThat(service.tryAcquireEmail(EMAIL)).isTrue();
    }

    @Test
    void 같은_IP의_동시_요청은_한도만큼만_통과한다() throws Exception {
        List<Boolean> results = concurrently(IP_LIMIT * 2, () -> service.tryAcquireIp(IP));

        assertThat(results.stream().filter(Boolean::booleanValue)).hasSize(IP_LIMIT);
    }

    private static <T> List<T> concurrently(int threads, Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
