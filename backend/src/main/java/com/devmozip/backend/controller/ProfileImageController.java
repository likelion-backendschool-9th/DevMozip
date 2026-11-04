package com.devmozip.backend.controller;

import com.devmozip.backend.service.ProfileImageService;
import com.devmozip.backend.service.ProfileImageService.ImageResponse;
import com.devmozip.backend.service.ProfileImageService.Kind;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/users/me/images")
@RequiredArgsConstructor
public class ProfileImageController {
    private final ProfileImageService service;

    @PutMapping(value = "/{kind}", consumes = "multipart/form-data")
    public ImageResponse upload(Authentication authentication, @PathVariable String kind, @RequestPart("file") MultipartFile file) {
        return service.upload(email(authentication), kind(kind), file);
    }

    @DeleteMapping("/{kind}")
    public ImageResponse delete(Authentication authentication, @PathVariable String kind) {
        return service.delete(email(authentication), kind(kind));
    }

    private String email(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        return authentication.getName();
    }

    private Kind kind(String value) {
        return switch (value) {
            case "profile" -> Kind.PROFILE;
            case "banner" -> Kind.BANNER;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "profile 또는 banner 이미지를 선택해 주세요.");
        };
    }
}
