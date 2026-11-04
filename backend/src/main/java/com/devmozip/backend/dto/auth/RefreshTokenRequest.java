package com.devmozip.backend.dto.auth;

/** POST /api/v1/auth/refresh, POST /api/v1/auth/logout 의 요청 본문 */
public record RefreshTokenRequest(String refreshToken) {}
