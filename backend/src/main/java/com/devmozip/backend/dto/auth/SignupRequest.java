package com.devmozip.backend.dto.auth;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /api/v1/auth/signup 의 요청 본문. 입력 규칙과 안내 문구는 프론트 validateSignup과 맞춘다. */
public record SignupRequest(
        @NotBlank(message = "올바른 이메일 주소를 입력해 주세요.")
        @Email(message = "올바른 이메일 주소를 입력해 주세요.")
        @Size(max = 254, message = "올바른 이메일 주소를 입력해 주세요.")
        String email,

        // 이메일 가입은 필수이고 카카오 가입은 받지 않는다. 필수 여부는 MemberService가 확인한다.
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).{8,64}$", flags = Pattern.Flag.DOTALL,
                message = "영문과 숫자를 포함해 8~64자로 입력해 주세요.")
        String password,

        String confirmPassword,

        @NotBlank(message = "한글·영문·숫자·공백·밑줄로 2~20자를 입력해 주세요.")
        @Pattern(regexp = "^[\\p{L}\\p{N}_ ]{2,20}$", message = "한글·영문·숫자·공백·밑줄로 2~20자를 입력해 주세요.")
        String nickname,

        @NotBlank(message = "개발 직군을 선택해 주세요.")
        @Size(max = 50, message = "개발 직군을 선택해 주세요.")
        String job,

        @NotBlank(message = "경력을 선택해 주세요.")
        @Size(max = 30, message = "경력을 선택해 주세요.")
        String career,

        @NotEmpty(message = "기술 스택을 하나 이상 선택해 주세요.")
        @Size(max = 30, message = "기술 스택은 30개까지 선택할 수 있습니다.")
        List<@NotBlank(message = "기술 스택을 하나 이상 선택해 주세요.") @Size(max = 50, message = "기술 스택을 다시 선택해 주세요.") String> stacks,

        @NotEmpty(message = "관심 분야를 하나 이상 선택해 주세요.")
        @Size(max = 30, message = "관심 분야는 30개까지 선택할 수 있습니다.")
        List<@NotBlank(message = "관심 분야를 하나 이상 선택해 주세요.") @Size(max = 50, message = "관심 분야를 다시 선택해 주세요.") String> interests,

        @NotNull(message = "협업 가능 여부를 선택해 주세요.")
        Boolean available,

        // 카카오 가입일 때만 보낸다. 카카오 회원번호는 요청 값으로 받지 않고 이 토큰으로만 확인한다.
        String kakaoSignupToken
) {}
