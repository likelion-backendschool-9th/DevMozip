package com.devmozip.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * POST /api/v1/auth/password/reset 의 요청 본문. 비밀번호 입력 규칙과 안내 문구는 회원가입과 같다.
 * 토큰이 없으면 400이 아니라 만료된 링크와 같은 401로 응답하므로 여기서 검사하지 않는다.
 */
public record PasswordResetRequest(
        String token,

        @NotNull(message = "영문과 숫자를 포함해 8~64자로 입력해 주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).{8,64}$", flags = Pattern.Flag.DOTALL,
                message = "영문과 숫자를 포함해 8~64자로 입력해 주세요.")
        String newPassword,

        @NotBlank(message = "비밀번호가 일치하지 않습니다.")
        String confirmPassword
) {}
