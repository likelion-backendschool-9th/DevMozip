package com.devmozip.backend.dto.profile;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileIntroductionRequest(
        @NotNull @Size(max = 20000) String introduction,
        @NotNull @Pattern(regexp = "text|html") String format
) {}
