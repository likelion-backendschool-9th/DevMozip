package com.devmozip.backend.service;

import java.time.Duration;
import java.util.Optional;

/**
 * 비밀번호 재설정 토큰과 요청 횟수를 기억해 두는 저장소.
 *
 * 토큰은 원문이 아니라 해시로만 받는다. 저장 값이 디스크에 남아도 메일 링크를 만들 수 없게 하기 위해서다.
 * 회원당 마지막으로 발급한 토큰 하나만 유효하며, 수명과 제한 값은 PasswordResetProperties를 따른다.
 *
 * - local 프로필: InMemoryPasswordResetService. Redis 없이 실행하며 서버를 재시작하면 사라진다.
 * - 그 외(서버·compose·테스트): RedisPasswordResetService. 블루그린의 두 백엔드가 같은 값을 본다.
 */
public interface PasswordResetService {

    /** 시간당 요청 제한을 세는 구간. 첫 요청부터 재며, 거절된 요청은 세지 않고 구간을 늘리지도 않는다. */
    Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);

    /** 새 토큰을 저장하고 이 회원의 이전 토큰을 무효로 만든다. */
    void saveToken(String email, String tokenHash);

    /** 토큰을 확인하고 바로 지운다. 동시에 요청해도 한 번만 이메일을 돌려주고, 없거나 만료됐으면 비어 있다. */
    Optional<String> consumeToken(String tokenHash);

    /** 이 회원의 아직 쓰지 않은 토큰을 지운다. */
    void removeToken(String email);

    /** 재요청 간격과 시간당 횟수를 모두 만족하면 한 번으로 세고 true. 초과하면 세지 않고 false */
    boolean tryAcquireEmail(String email);

    /** 시간당 횟수를 만족하면 한 번으로 세고 true. 초과하면 세지 않고 false */
    boolean tryAcquireIp(String ip);
}
