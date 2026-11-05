package com.devmozip.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devmozip.backend.dto.auth.KakaoAuthorizeResponse;
import com.devmozip.backend.dto.auth.KakaoCallbackResponse;
import com.devmozip.backend.service.KakaoAuthService;

@RestController
@RequestMapping("/api/v1/auth/kakao")
public class KakaoAuthController {

    private final KakaoAuthService kakaoAuthService;

    public KakaoAuthController(KakaoAuthService kakaoAuthService) {
        this.kakaoAuthService = kakaoAuthService;
    }

    /** 카카오 인가 주소. state는 프론트엔드 BFF가 만들어 콜백에서 대조한다. */
    @GetMapping
    public KakaoAuthorizeResponse authorize(@RequestParam String state) {
        return kakaoAuthService.authorize(state);
    }

    /** 인가 코드로 회원을 찾는다. 쿠키 저장과 화면 이동은 프론트엔드 BFF가 맡는다. */
    @GetMapping("/callback")
    public KakaoCallbackResponse callback(@RequestParam String code,
                                          @RequestParam(defaultValue = "login") String intent) {
        return kakaoAuthService.handleCallback(code, intent);
    }
}
