package com.devmozip.backend.dto.auth;

/** POST /api/auth/refresh, POST /api/auth/logout 의 요청 본문 */
public record RefreshTokenRequest(String refreshToken) {}
