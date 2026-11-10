package com.devmozip.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * POST /api/v1/auth/signup 의 응답 본문.
 * 이메일 가입은 토큰 없이 응답하고 프론트엔드가 가입 직후 로그인을 따로 호출한다.
 * 카카오 가입은 비밀번호로 로그인할 수 없으므로 토큰을 함께 돌려준다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignupResponse(
        Long id,
        String email,
        String nickname,
        String accessToken,
        String refreshToken,
        String tokenType
) {

    public SignupResponse(Long id, String email, String nickname) {
        this(id, email, nickname, null, null, null);
    }
}
