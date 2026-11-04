package com.devmozip.backend.security.jwt;

/**
 * 발급한 리프레시 토큰을 기억해 두는 저장소.
 *
 * 액세스 토큰은 서명만 맞으면 통과시키지만(STATELESS),
 * 리프레시 토큰은 수명이 길어서 "서버가 아직 인정하는 토큰인지"를 따로 확인해야 한다.
 * 로그아웃하면 여기서 지우고, 그 뒤로는 같은 토큰으로 재발급받을 수 없다.
 * 사용자당 마지막으로 발급한 토큰 하나만 기억하므로 새 로그인이 이전 토큰을 대체한다.
 *
 * - local 프로필: InMemoryRefreshTokenStore. Redis 없이 실행하며 서버를 재시작하면 사라진다.
 * - 그 외(서버·compose·테스트): RedisRefreshTokenStore. 재시작·블루그린 전환 후에도 유지된다.
 */
public interface RefreshTokenStore {

    void save(String username, String refreshToken);

    /** 저장된 토큰과 같은지 확인한다. 로그아웃했거나 다른 기기에서 다시 로그인했으면 false */
    boolean matches(String username, String refreshToken);

    void remove(String username);

    /** 저장된 토큰이 이 토큰일 때만 지운다. 이전 로그인의 로그아웃이 새 로그인까지 지우지 않게 한다. */
    void removeMatching(String username, String refreshToken);
}
