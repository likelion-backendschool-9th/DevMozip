package com.devmozip.backend.service;

import com.devmozip.backend.dto.profile.ProfilePostsPageResponse;

public interface ProfilePostsService {
	ProfilePostsPageResponse own(String email, int page, int size);
	ProfilePostsPageResponse publicList(String slug, int page, int size);
}
