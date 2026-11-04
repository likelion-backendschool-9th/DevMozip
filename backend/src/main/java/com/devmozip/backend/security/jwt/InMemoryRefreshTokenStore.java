package com.devmozip.backend.security.jwt;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** local(H2) 프로필용 저장소. Redis 없이 실행하며 서버를 재시작하면 모두 사라진다. */
@Component
@Profile("local")
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

    // key: 사용자 아이디, value: 그 사용자에게 마지막으로 발급한 리프레시 토큰
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    @Override
    public void save(String username, String refreshToken) {
        tokens.put(username, refreshToken);
    }

    @Override
    public boolean matches(String username, String refreshToken) {
        return refreshToken != null && refreshToken.equals(tokens.get(username));
    }

    @Override
    public void remove(String username) {
        tokens.remove(username);
    }

    @Override
    public void removeMatching(String username, String refreshToken) {
        // 값까지 같을 때만 지운다. ConcurrentHashMap이 비교와 삭제를 한 번에 처리한다.
        tokens.remove(username, refreshToken);
    }
}
