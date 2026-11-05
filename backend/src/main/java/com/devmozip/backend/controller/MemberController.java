package com.devmozip.backend.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.devmozip.backend.dto.member.AvailabilityRequest;
import com.devmozip.backend.dto.member.AvailabilityResponse;
import com.devmozip.backend.dto.member.PasswordSetRequest;
import com.devmozip.backend.service.MemberService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // 내 협업 가능 상태와 연락 가능 시간 수정 - 토큰의 주인만 바꿀 수 있다
    @PatchMapping("/me/availability")
    public AvailabilityResponse updateAvailability(Authentication authentication,
                                                   @Valid @RequestBody AvailabilityRequest request) {
        return memberService.updateAvailability(authentication.getName(), request);
    }

    // 비밀번호 없는 카카오 회원의 비밀번호 설정 - 토큰의 주인만 바꿀 수 있다
    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPassword(Authentication authentication,
                            @Valid @RequestBody PasswordSetRequest request) {
        memberService.setPassword(authentication.getName(), request);
    }

    // 로그인 확인 - 프론트엔드 BFF가 쿠키의 액세스 토큰 주인과 역할을 확인한다
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> getMe(Authentication authentication) {
        // JwtAuthenticationFilter는 토큰의 역할 하나에 "ROLE_" 접두어를 붙여 넣는다.
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.replaceFirst("^ROLE_", ""))
                .findFirst()
                .orElse("USER");
        return ResponseEntity.ok(Map.of("username", authentication.getName(), "role", role));
    }
}
