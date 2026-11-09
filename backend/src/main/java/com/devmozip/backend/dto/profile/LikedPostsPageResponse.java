package com.devmozip.backend.dto.profile;

import java.util.List;

public record LikedPostsPageResponse(List<LikedPostResponse> items, int page, int size, long totalElements, long totalPages) {}
