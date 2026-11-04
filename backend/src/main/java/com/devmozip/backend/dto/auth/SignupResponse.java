package com.devmozip.backend.dto.auth;

/** POST /api/v1/auth/signup 의 응답 본문. 토큰은 없으며 프론트엔드가 가입 직후 로그인을 따로 호출한다. */
public record SignupResponse(Long id, String email, String nickname) {}
