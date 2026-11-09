package com.devmozip.backend.controller;

import com.devmozip.backend.dto.profile.ProfileLinksRequest;
import com.devmozip.backend.dto.profile.ProfileLinksResponse;
import com.devmozip.backend.service.ProfileLinksService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class ProfileLinksController {
    private final ProfileLinksService service;

    @GetMapping("/{slug}/links")
    public ProfileLinksResponse publicLinks(@PathVariable String slug) {
        return service.findPublic(slug);
    }

    @GetMapping("/me/links")
    public ProfileLinksResponse ownLinks(Authentication authentication) {
        return service.findOwn(email(authentication));
    }

    @PutMapping("/me/links")
    public ProfileLinksResponse update(Authentication authentication, @Valid @RequestBody ProfileLinksRequest request) {
        return service.update(email(authentication), request);
    }

    private String email(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        return authentication.getName();
    }
}
