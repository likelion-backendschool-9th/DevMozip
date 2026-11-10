package com.devmozip.backend.dto.portfolio;

import java.util.List;
public record PortfolioPageResponse(List<PortfolioSummaryResponse> items, int page, int size,
        long totalElements, long totalPages) {}
