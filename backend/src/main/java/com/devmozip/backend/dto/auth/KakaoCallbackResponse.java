package com.devmozip.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

/** GET /api/v1/auth/kakao/callback 의 응답 본문. 상태에 해당하는 값만 내려보낸다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KakaoCallbackResponse(
        Status status,
        String accessToken,
        String refreshToken,
        String tokenType,
        String kakaoSignupToken
) {

    public enum Status {
        LOGIN,              // 등록된 회원. 토큰을 발급했다
        SIGNUP_REQUIRED,    // 등록되지 않은 카카오 계정. 가입 토큰으로 회원가입한다
        ALREADY_REGISTERED  // 가입에서 시작했지만 이미 등록된 카카오 계정이다
    }

    public static KakaoCallbackResponse login(LoginResponse tokens) {
        return new KakaoCallbackResponse(Status.LOGIN, tokens.accessToken(), tokens.refreshToken(), tokens.tokenType(), null);
    }

    public static KakaoCallbackResponse signupRequired(String kakaoSignupToken) {
        return new KakaoCallbackResponse(Status.SIGNUP_REQUIRED, null, null, null, kakaoSignupToken);
    }

    public static KakaoCallbackResponse alreadyRegistered() {
        return new KakaoCallbackResponse(Status.ALREADY_REGISTERED, null, null, null, null);
    }
}
