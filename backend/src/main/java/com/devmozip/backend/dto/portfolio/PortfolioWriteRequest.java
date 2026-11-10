package com.devmozip.backend.dto.portfolio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public record PortfolioWriteRequest(
    @NotBlank @Size(max=100) String title,
    @Size(max=2048) String thumbnailUrl,
    @Size(max=255) String topic,
    @NotBlank @Size(max=200) String summary,
    @Size(max=200000) String description,
    @NotBlank @Size(max=100) String role,
    @NotEmpty @Size(max=30) List<@NotBlank @Size(max=100) String> techStack,
    @Size(max=100) List<@NotNull @Valid PortfolioAttachmentRequest> attachments,
    @Pattern(regexp="PUBLIC|PRIVATE") String visibility,
    PortfolioDocument document,
    LocalDateTime expectedUpdatedAt
) {
    public record PortfolioAttachmentRequest(@Size(max=255) String name,@NotBlank @Size(max=2048) String url) {}
}
