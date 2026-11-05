package com.devmozip.backend.controller;

import com.devmozip.backend.dto.profile.ProfileOptionsRequest;
import com.devmozip.backend.dto.profile.ProfileOptionsResponse;
import com.devmozip.backend.service.ProfileOptionsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class ProfileOptionsController {
    private final ProfileOptionsService service;

    @GetMapping("/{slug}/options")
    public ProfileOptionsResponse publicOptions(@PathVariable String slug) {
        return service.findPublic(slug);
    }

    @GetMapping("/me/options")
    public ProfileOptionsResponse ownOptions(Authentication authentication) {
        return service.findOwn(email(authentication));
    }

    @PutMapping("/me/options")
    public ProfileOptionsResponse update(Authentication authentication, @Valid @RequestBody ProfileOptionsRequest request) {
        return service.update(email(authentication), request);
    }

    private String email(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        return authentication.getName();
    }
}
