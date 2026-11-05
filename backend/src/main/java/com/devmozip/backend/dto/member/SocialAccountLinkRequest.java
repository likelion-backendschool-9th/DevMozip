package com.devmozip.backend.dto.member;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/users/me/social-accounts/{providerId} 의 요청 본문. 카카오 인증에서 받은 인가 코드다. */
public record SocialAccountLinkRequest(
        @NotBlank(message = "카카오 인가 코드가 필요합니다.")
        String code
) {}
