package com.devmozip.backend.dto.portfolio;

import java.util.List;
public record PortfolioDetailResponse(PortfolioSummaryResponse portfolio, Author author,
        String description, List<Attachment> attachments, PortfolioDocument document, boolean canEdit, boolean submitted) {
    public record Author(long id, String nickname, String tag, String profileImage) {}
    public record Attachment(String name, String url) {}
}
