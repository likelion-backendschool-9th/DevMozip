package com.devmozip.backend.dto.project;

import java.util.List;

public record ProjectActivityPageResponse(List<ProjectActivityResponse> items, long totalElements,
	int page, int size, long totalPages) {}
