package com.devmozip.backend.controller;

import com.devmozip.backend.dto.portfolio.PortfolioFileResponse;
import com.devmozip.backend.service.PortfolioFileService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequiredArgsConstructor
public class PortfolioFileController {
    private final PortfolioFileService service;
    @PostMapping(value="/api/v1/files", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PortfolioFileResponse> upload(Authentication auth, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.upload(auth == null ? null : auth.getName(), file));
    }
    @GetMapping("/api/v1/files/{id}")
    public ResponseEntity<byte[]> read(Authentication auth, @PathVariable String id) {
        var file = service.read(auth == null ? null : auth.getName(), id); var meta = file.metadata();
        boolean image = meta.contentType().startsWith("image/");
        var disposition = (image ? ContentDisposition.inline() : ContentDisposition.attachment()).filename(meta.name(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(meta.contentType()))
                .header("Content-Disposition", disposition.toString()).header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox").body(file.bytes());
    }
}
