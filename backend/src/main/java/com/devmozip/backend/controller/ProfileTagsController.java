package com.devmozip.backend.controller;

import com.devmozip.backend.dto.profile.ProfileTagsRequest;
import com.devmozip.backend.dto.profile.ProfileTagsResponse;
import com.devmozip.backend.service.ProfileTagsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class ProfileTagsController {
    private final ProfileTagsService service;

    @GetMapping("/{slug}/tags")
    public ProfileTagsResponse publicTags(@PathVariable String slug) {
        return service.findPublic(slug);
    }

    @GetMapping("/me/tags")
    public ProfileTagsResponse ownTags(Authentication authentication) {
        return service.findOwn(email(authentication));
    }

    @PutMapping("/me/tags")
    public ProfileTagsResponse update(Authentication authentication, @Valid @RequestBody ProfileTagsRequest request) {
        return service.update(email(authentication), request);
    }

    private String email(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        return authentication.getName();
    }
}
