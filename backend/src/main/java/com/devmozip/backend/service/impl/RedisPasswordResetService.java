package com.devmozip.backend.service.impl;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import com.devmozip.backend.config.PasswordResetProperties;
import com.devmozip.backend.service.PasswordResetService;

/**
 * 서버용 저장소. 여러 키를 함께 바꾸는 작업은 Lua 스크립트로 한 번에 실행해 동시 요청이 끼어들지 못하게 한다.
 * 모든 키에 수명을 두어 Redis가 스스로 지운다.
 */
@Service
@Profile("!local")
public class RedisPasswordResetService implements PasswordResetService {

    private static final String TOKEN_PREFIX = "password-reset:token:";
    private static final String MEMBER_PREFIX = "password-reset:member:";
    private static final String COOLDOWN_PREFIX = "password-reset:cooldown:";
    private static final String EMAIL_COUNT_PREFIX = "password-reset:count:email:";
    private static final String IP_COUNT_PREFIX = "password-reset:count:ip:";

    // 이전 토큰의 키는 회원 키의 값으로만 알 수 있어 스크립트 안에서 만든다 (compose·서버의 Redis는 단일 노드)
    private static final RedisScript<Long> SAVE_TOKEN = RedisScript.of("""
            local old = redis.call('get', KEYS[1])
            if old then redis.call('del', ARGV[1] .. old) end
            redis.call('set', KEYS[2], ARGV[2], 'PX', ARGV[4])
            redis.call('set', KEYS[1], ARGV[3], 'PX', ARGV[4])
            return 1
            """, Long.class);

    private static final RedisScript<String> CONSUME_TOKEN = RedisScript.of("""
            local email = redis.call('getdel', KEYS[1])
            if not email then return false end
            local member = ARGV[1] .. email
            if redis.call('get', member) == ARGV[2] then redis.call('del', member) end
            return email
            """, String.class);

    private static final RedisScript<Long> REMOVE_TOKEN = RedisScript.of("""
            local hash = redis.call('get', KEYS[1])
            if hash then redis.call('del', ARGV[1] .. hash, KEYS[1]) end
            return 1
            """, Long.class);

    private static final RedisScript<Long> ACQUIRE_EMAIL = RedisScript.of("""
            if redis.call('exists', KEYS[1]) == 1 then return 0 end
            if tonumber(redis.call('get', KEYS[2]) or '0') >= tonumber(ARGV[2]) then return 0 end
            redis.call('set', KEYS[1], '1', 'PX', ARGV[1])
            if redis.call('incr', KEYS[2]) == 1 then redis.call('pexpire', KEYS[2], ARGV[3]) end
            return 1
            """, Long.class);

    private static final RedisScript<Long> ACQUIRE_IP = RedisScript.of("""
            if tonumber(redis.call('get', KEYS[1]) or '0') >= tonumber(ARGV[1]) then return 0 end
            if redis.call('incr', KEYS[1]) == 1 then redis.call('pexpire', KEYS[1], ARGV[2]) end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final PasswordResetProperties properties;

    public RedisPasswordResetService(StringRedisTemplate redis, PasswordResetProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public void saveToken(String email, String tokenHash) {
        redis.execute(SAVE_TOKEN, List.of(MEMBER_PREFIX + email, TOKEN_PREFIX + tokenHash),
                TOKEN_PREFIX, email, tokenHash, String.valueOf(properties.getTokenTtl().toMillis()));
    }

    @Override
    public Optional<String> consumeToken(String tokenHash) {
        return Optional.ofNullable(redis.execute(CONSUME_TOKEN, List.of(TOKEN_PREFIX + tokenHash), MEMBER_PREFIX, tokenHash));
    }

    @Override
    public void removeToken(String email) {
        redis.execute(REMOVE_TOKEN, List.of(MEMBER_PREFIX + email), TOKEN_PREFIX);
    }

    @Override
    public boolean tryAcquireEmail(String email) {
        Long acquired = redis.execute(ACQUIRE_EMAIL, List.of(COOLDOWN_PREFIX + email, EMAIL_COUNT_PREFIX + email),
                String.valueOf(properties.getEmailCooldown().toMillis()),
                String.valueOf(properties.getEmailHourlyLimit()),
                String.valueOf(RATE_LIMIT_WINDOW.toMillis()));
        return Long.valueOf(1).equals(acquired);
    }

    @Override
    public boolean tryAcquireIp(String ip) {
        Long acquired = redis.execute(ACQUIRE_IP, List.of(IP_COUNT_PREFIX + ip),
                String.valueOf(properties.getIpHourlyLimit()),
                String.valueOf(RATE_LIMIT_WINDOW.toMillis()));
        return Long.valueOf(1).equals(acquired);
    }
}
