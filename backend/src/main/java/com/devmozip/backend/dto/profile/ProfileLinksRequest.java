package com.devmozip.backend.dto.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProfileLinksRequest(
        @NotNull @Size(max = 500) String github,
        @NotNull @Size(max = 500) String blog,
        @NotNull @Size(max = 10) List<@NotNull @Valid Link> externalLinks
) {
    public record Link(@NotBlank @Size(max = 40) String label, @NotBlank @Size(max = 500) String url) {}
}
