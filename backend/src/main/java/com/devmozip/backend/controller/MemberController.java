package com.devmozip.backend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class MemberController {

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
