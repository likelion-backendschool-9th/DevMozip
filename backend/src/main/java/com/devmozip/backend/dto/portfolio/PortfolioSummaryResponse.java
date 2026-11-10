package com.devmozip.backend.dto.portfolio;

import java.time.LocalDateTime;
import java.util.List;

/** 공개 기본 정보만 제공한다. 상세 설명과 첨부파일은 포함하지 않는다. */
public record PortfolioSummaryResponse(long id, long userId, String title, String thumbnailUrl,
        String topic, String summary, String role, List<String> techStack, String visibility,
        LocalDateTime createdAt, LocalDateTime updatedAt) {}
