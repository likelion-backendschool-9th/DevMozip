package com.devmozip.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devmozip.backend.dto.member.MemberRequest;
import com.devmozip.backend.dto.member.MemberResponse;
import com.devmozip.backend.service.MemberService;

@RestController
@RequestMapping("/api/v1/users")
public class MemberController {
    private final MemberService service;

    public MemberController(MemberService service) {
        this.service = service;
    }

    // 3.1 공통 프로필 조회 - 페이지 /users/{slug}
    @GetMapping("/{slug}")
    public ResponseEntity<MemberResponse> getProfile(@PathVariable String slug) {
        return ResponseEntity.ok(service.getProfile(slug));
    }

    // 2.8 초기 개발자 프로필 설정, 3.2 프로필 수정 - 페이지 /onboarding, /settings/profile
    @PutMapping("/me/profile")
    public ResponseEntity<MemberResponse> updateProfile(Authentication authentication,
                                                        @RequestBody MemberRequest request) {
        // JWT 필터가 넣어 둔 인증 정보의 이름이 로그인한 회원의 이메일이다.
        return ResponseEntity.ok(service.updateProfile(authentication.getName(), request));
    }

    // 3.5 외부 링크 관리 - 페이지 /settings/profile
    @PutMapping("/me/links")
    public ResponseEntity<MemberResponse> updateLinks(Authentication authentication,
                                                      @RequestBody MemberRequest request) {
        return ResponseEntity.ok(service.updateLinks(authentication.getName(), request));
    }

    // 3.6 협업 가능 상태 관리 - 페이지 /settings/profile
    @PatchMapping("/me/availability")
    public ResponseEntity<MemberResponse> updateAvailability(Authentication authentication,
                                                             @RequestBody MemberRequest request) {
        return ResponseEntity.ok(service.updateAvailability(authentication.getName(), request));
    }
}
