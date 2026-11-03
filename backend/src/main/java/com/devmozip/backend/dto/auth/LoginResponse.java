package com.devmozip.backend.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String accessToken;
    /** 액세스 토큰이 만료됐을 때 새 액세스 토큰을 받기 위한 토큰. 수명이 길다(14일). */
    private String refreshToken;
    private String tokenType;
}
