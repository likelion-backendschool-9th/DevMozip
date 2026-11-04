package com.devmozip.backend.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProfileTagsRequest(
        @NotNull @Size(max = 30) List<@NotBlank @Size(max = 50) String> skills,
        @NotNull @Size(max = 30) List<@NotBlank @Size(max = 50) String> interests
) {}
