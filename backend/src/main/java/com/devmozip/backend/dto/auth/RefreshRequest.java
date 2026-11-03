package com.devmozip.backend.dto.auth;

import lombok.Data;
import lombok.NoArgsConstructor;

/** POST /api/auth/refresh, POST /api/auth/logout 의 요청 본문 */
@Data
@NoArgsConstructor
public class RefreshRequest {
    private String refreshToken;
}
