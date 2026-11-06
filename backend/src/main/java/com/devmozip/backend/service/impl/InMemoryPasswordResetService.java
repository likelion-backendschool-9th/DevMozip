package com.devmozip.backend.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.devmozip.backend.config.PasswordResetProperties;
import com.devmozip.backend.service.PasswordResetService;

/** local(H2) 프로필용 저장소. Redis 없이 실행하며 서버를 재시작하면 모두 사라진다. 동작은 Redis 구현과 같다. */
@Service
@Profile("local")
public class InMemoryPasswordResetService implements PasswordResetService {

    private record Entry(String value, Instant expiresAt) {}

    private static final String TOKEN_PREFIX = "token:";
    private static final String MEMBER_PREFIX = "member:";
    private static final String COOLDOWN_PREFIX = "cooldown:";
    private static final String EMAIL_COUNT_PREFIX = "count:email:";
    private static final String IP_COUNT_PREFIX = "count:ip:";

    // 여러 키를 함께 바꾸므로 메서드 전체를 synchronized로 묶는다
    private final Map<String, Entry> entries = new HashMap<>();
    private final PasswordResetProperties properties;
    private final Clock clock;

    @Autowired
    public InMemoryPasswordResetService(PasswordResetProperties properties) {
        this(properties, Clock.systemUTC());
    }

    InMemoryPasswordResetService(PasswordResetProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public synchronized void saveToken(String email, String tokenHash) {
        removeToken(email);
        put(TOKEN_PREFIX + tokenHash, email, properties.getTokenTtl());
        put(MEMBER_PREFIX + email, tokenHash, properties.getTokenTtl());
    }

    @Override
    public synchronized Optional<String> consumeToken(String tokenHash) {
        removeExpired();
        Entry token = entries.remove(TOKEN_PREFIX + tokenHash);
        if (token == null) {
            return Optional.empty();
        }
        entries.remove(MEMBER_PREFIX + token.value());
        return Optional.of(token.value());
    }

    @Override
    public synchronized void removeToken(String email) {
        removeExpired();
        Entry member = entries.remove(MEMBER_PREFIX + email);
        if (member != null) {
            entries.remove(TOKEN_PREFIX + member.value());
        }
    }

    @Override
    public synchronized boolean tryAcquireEmail(String email) {
        removeExpired();
        if (entries.containsKey(COOLDOWN_PREFIX + email)
                || count(EMAIL_COUNT_PREFIX + email) >= properties.getEmailHourlyLimit()) {
            return false;
        }
        put(COOLDOWN_PREFIX + email, "1", properties.getEmailCooldown());
        increment(EMAIL_COUNT_PREFIX + email);
        return true;
    }

    @Override
    public synchronized boolean tryAcquireIp(String ip) {
        removeExpired();
        if (count(IP_COUNT_PREFIX + ip) >= properties.getIpHourlyLimit()) {
            return false;
        }
        increment(IP_COUNT_PREFIX + ip);
        return true;
    }

    private void put(String key, String value, Duration ttl) {
        entries.put(key, new Entry(value, clock.instant().plus(ttl)));
    }

    private int count(String key) {
        Entry entry = entries.get(key);
        return entry == null ? 0 : Integer.parseInt(entry.value());
    }

    // 구간은 첫 요청부터 재므로 만료 시각을 유지한다
    private void increment(String key) {
        Entry entry = entries.get(key);
        Instant expiresAt = entry == null ? clock.instant().plus(RATE_LIMIT_WINDOW) : entry.expiresAt();
        entries.put(key, new Entry(String.valueOf(count(key) + 1), expiresAt));
    }

    private void removeExpired() {
        Instant now = clock.instant();
        entries.values().removeIf(entry -> !now.isBefore(entry.expiresAt()));
    }
}
