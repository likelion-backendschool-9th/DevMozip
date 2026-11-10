package com.devmozip.backend.dto.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** PATCH /api/v1/users/me/password 의 요청 본문. 입력 규칙과 안내 문구는 회원가입과 같다. */
public record PasswordSetRequest(
        // 비밀번호가 있는 회원만 필수다. 필수 여부는 회원 상태를 아는 MemberService가 확인한다.
        String currentPassword,

        @NotNull(message = "영문과 숫자를 포함해 8~64자로 입력해 주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).{8,64}$", flags = Pattern.Flag.DOTALL,
                message = "영문과 숫자를 포함해 8~64자로 입력해 주세요.")
        String newPassword,

        @NotBlank(message = "비밀번호가 일치하지 않습니다.")
        String confirmPassword
) {}
