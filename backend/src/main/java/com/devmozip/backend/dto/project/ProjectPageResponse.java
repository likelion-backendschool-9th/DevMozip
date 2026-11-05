package com.devmozip.backend.dto.project;

import java.util.List;

public record ProjectPageResponse(List<ProjectListItemResponse> items, long totalElements, int page, int size, long totalPages) {}
