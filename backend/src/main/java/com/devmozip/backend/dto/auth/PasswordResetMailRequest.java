package com.devmozip.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/v1/auth/password/reset-request 의 요청 본문. 입력 규칙과 안내 문구는 회원가입과 같다. */
public record PasswordResetMailRequest(
        @NotBlank(message = "올바른 이메일 주소를 입력해 주세요.")
        @Email(message = "올바른 이메일 주소를 입력해 주세요.")
        @Size(max = 254, message = "올바른 이메일 주소를 입력해 주세요.")
        String email
) {}
