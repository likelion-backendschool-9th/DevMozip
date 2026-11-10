package com.devmozip.backend.dto.profile;

import java.util.List;

public record ProfilePostsPageResponse(List<ProfilePostResponse> items, int page, int size,
		long totalElements, long totalPages) {}
