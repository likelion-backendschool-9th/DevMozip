package com.devmozip.backend.controller;

import com.devmozip.backend.dto.profile.ProfileDetailResponse;
import com.devmozip.backend.dto.profile.ProfileUpdateRequest;
import com.devmozip.backend.service.ProfileUpdateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/users/me/profile")
@RequiredArgsConstructor
public class ProfileUpdateController {
    private final ProfileUpdateService service;
    private final com.devmozip.backend.service.ProfileService profiles;

    @GetMapping
    public ProfileDetailResponse own(Authentication authentication) {
        return profiles.findOwn(email(authentication));
    }

    @GetMapping("/job-categories")
    public java.util.List<ProfileDetailResponse.JobCategoryResponse> jobs(Authentication authentication) {
        return profiles.jobCategories(email(authentication));
    }

    private String email(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return authentication.getName();
    }


    @PutMapping
    public ProfileDetailResponse update(Authentication authentication, @Valid @RequestBody ProfileUpdateRequest request) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return service.update(authentication.getName(), request);
    }
}
