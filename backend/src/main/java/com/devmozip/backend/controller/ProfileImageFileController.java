package com.devmozip.backend.controller;

import com.devmozip.backend.service.ProfileImageService;
import com.devmozip.backend.storage.LocalProfileImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ProfileImageFileController {
    private final ProfileImageService service;

    @GetMapping("/api/v1/profile-images/{filename}")
    public ResponseEntity<byte[]> read(@PathVariable String filename) {
        byte[] bytes = service.readPublicImage(filename);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(LocalProfileImageStorage.contentType(filename)))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline").body(bytes);
    }
}
