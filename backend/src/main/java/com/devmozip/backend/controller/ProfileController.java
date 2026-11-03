package com.devmozip.backend.controller;

import com.devmozip.backend.dto.profile.ProfileDetailResponse;
import com.devmozip.backend.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class ProfileController {
    private final ProfileService profileService;

    @GetMapping("/{slug}")
    public ProfileDetailResponse findBySlug(@PathVariable String slug) {
        return profileService.findBySlug(slug);
    }
}
