package com.devmozip.backend.security.jwt;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 발급한 리프레시 토큰을 기억해 두는 저장소.
 *
 * 액세스 토큰은 서명만 맞으면 통과시키지만(STATELESS),
 * 리프레시 토큰은 수명이 길어서 "서버가 아직 인정하는 토큰인지"를 따로 확인해야 한다.
 * 로그아웃하면 여기서 지우고, 그 뒤로는 같은 토큰으로 재발급받을 수 없다.
 *
 * 지금은 메모리(Map)에 두므로 서버를 재시작하면 모두 사라진다. DB 수업에서 테이블로 옮긴다.
 */
@Component
public class RefreshTokenStore {

    // key: 사용자 아이디, value: 그 사용자에게 마지막으로 발급한 리프레시 토큰
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    public void save(String username, String refreshToken) {
        tokens.put(username, refreshToken);
    }

    /** 저장된 토큰과 같은지 확인한다. 로그아웃했거나 다른 기기에서 다시 로그인했으면 false */
    public boolean matches(String username, String refreshToken) {
        return refreshToken.equals(tokens.get(username));
    }

    public void remove(String username) {
        tokens.remove(username);
    }
}
