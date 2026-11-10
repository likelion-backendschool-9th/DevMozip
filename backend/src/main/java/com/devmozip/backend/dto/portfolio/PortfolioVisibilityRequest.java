package com.devmozip.backend.dto.portfolio;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record PortfolioVisibilityRequest(@NotNull Visibility visibility, LocalDateTime expectedUpdatedAt) {
    public enum Visibility { PUBLIC, PRIVATE }
}
