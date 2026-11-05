package com.devmozip.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.devmozip.backend.dto.member.SocialAccountLinkRequest;
import com.devmozip.backend.dto.member.SocialAccountsResponse;
import com.devmozip.backend.service.KakaoAuthService;
import com.devmozip.backend.service.MemberService;

import jakarta.validation.Valid;

/** 소셜 계정 연동 조회·연결·해제. 대상 회원은 항상 토큰의 주인이다. */
@RestController
@RequestMapping("/api/v1/users/me/social-accounts")
public class SocialAccountController {

    private static final String KAKAO = "kakao";

    private final MemberService memberService;
    private final KakaoAuthService kakaoAuthService;

    public SocialAccountController(MemberService memberService, KakaoAuthService kakaoAuthService) {
        this.memberService = memberService;
        this.kakaoAuthService = kakaoAuthService;
    }

    @GetMapping
    public SocialAccountsResponse getSocialAccounts(Authentication authentication) {
        return memberService.getSocialAccounts(authentication.getName());
    }

    /** providerId는 제공자 이름이다. 카카오 회원번호는 인가 코드로 카카오에서 직접 확인한다. */
    @PostMapping("/{providerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void link(Authentication authentication,
                     @PathVariable String providerId,
                     @Valid @RequestBody SocialAccountLinkRequest request) {
        requireKakao(providerId);
        kakaoAuthService.link(authentication.getName(), request.code());
    }

    /** 카카오 연결을 끊으려면 새 액세스 토큰이 필요하므로 다시 받은 인가 코드를 함께 보낸다. */
    @DeleteMapping("/{providerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(Authentication authentication,
                       @PathVariable String providerId,
                       @RequestParam String code) {
        requireKakao(providerId);
        kakaoAuthService.unlink(authentication.getName(), code);
    }

    private void requireKakao(String providerId) {
        if (!KAKAO.equals(providerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "지원하지 않는 소셜 계정입니다.");
        }
    }
}
