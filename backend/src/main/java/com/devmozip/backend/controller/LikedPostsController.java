package com.devmozip.backend.controller;

import com.devmozip.backend.dto.profile.LikedPostsPageResponse;
import com.devmozip.backend.service.LikedPostsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/users/me/liked-posts")
@RequiredArgsConstructor
public class LikedPostsController {
    private final LikedPostsService service;
    @GetMapping
    public LikedPostsPageResponse list(Authentication authentication,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return service.findOwn(authentication.getName(),page,size);
    }
}
