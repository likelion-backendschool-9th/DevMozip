package com.devmozip.backend.dto.auth;

import lombok.Builder;

@Builder
public record LoginResponse(
    String accessToken,
    /** 액세스 토큰이 만료됐을 때 새 액세스 토큰을 받기 위한 토큰. 수명이 길다(14일). */
    String refreshToken,
    String tokenType
) {}
