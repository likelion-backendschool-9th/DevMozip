package com.devmozip.backend.controller;

import com.devmozip.backend.dto.profile.ProfileIntroductionRequest;
import com.devmozip.backend.dto.profile.ProfileIntroductionResponse;
import com.devmozip.backend.service.ProfileIntroductionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class ProfileIntroductionController {
    private final ProfileIntroductionService service;

    @GetMapping("/{slug}/introduction")
    public ProfileIntroductionResponse publicIntroduction(@PathVariable String slug) {
        return service.findPublic(slug);
    }

    @GetMapping("/me/introduction")
    public ProfileIntroductionResponse ownIntroduction(Authentication authentication) {
        return service.findOwn(email(authentication));
    }

    @PutMapping("/me/introduction")
    public ProfileIntroductionResponse update(Authentication authentication, @Valid @RequestBody ProfileIntroductionRequest request) {
        return service.update(email(authentication), request);
    }

    private String email(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        return authentication.getName();
    }
}
