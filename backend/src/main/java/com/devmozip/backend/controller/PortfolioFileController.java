package com.devmozip.backend.controller;

import com.devmozip.backend.dto.portfolio.PortfolioFileResponse;
import com.devmozip.backend.service.PortfolioFileService;
import com.devmozip.backend.storage.LocalPortfolioFileStorage.Metadata;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class PortfolioFileController {
    /** nginx의 내부 전용 경로. FILE_STORAGE 폴더와 연결하며 외부 요청으로는 열 수 없다 (nginx/server.conf, nginx/local.conf). */
    public static final String INTERNAL_PREFIX = "/internal-files/";
    private final PortfolioFileService service;
    private final boolean accelRedirect;
    public PortfolioFileController(PortfolioFileService service, @Value("${FILE_ACCEL_REDIRECT:false}") boolean accelRedirect) {
        this.service = service; this.accelRedirect = accelRedirect;
    }
    @PostMapping(value="/api/v1/files", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PortfolioFileResponse> upload(Authentication auth, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.upload(auth == null ? null : auth.getName(), file));
    }
    @GetMapping("/api/v1/files/{id}")
    public ResponseEntity<byte[]> read(Authentication auth, @PathVariable String id) {
        String email = auth == null ? null : auth.getName();
        if (accelRedirect) {
            // 권한 확인은 여기서 끝났다. 파일 내용은 nginx가 내부 전용 경로에서 읽어 보낸다(docs/portfolio-files-api.md).
            var file = service.locate(email, id);
            return headers(file.metadata()).header("X-Accel-Redirect", INTERNAL_PREFIX + file.storedName()).build();
        }
        var file = service.read(email, id);
        return headers(file.metadata()).body(file.bytes());
    }
    private static ResponseEntity.BodyBuilder headers(Metadata meta) {
        boolean image = meta.contentType().startsWith("image/");
        var disposition = (image ? ContentDisposition.inline() : ContentDisposition.attachment()).filename(meta.name(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(meta.contentType()))
                .header("Content-Disposition", disposition.toString()).header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox");
    }
}
