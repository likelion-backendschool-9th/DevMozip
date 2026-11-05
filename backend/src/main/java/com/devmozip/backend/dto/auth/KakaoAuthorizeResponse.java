package com.devmozip.backend.dto.auth;

/** GET /api/v1/auth/kakao 의 응답 본문. 브라우저를 보낼 카카오 인가 주소다. */
public record KakaoAuthorizeResponse(String authorizeUrl) {}
