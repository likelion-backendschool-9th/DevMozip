package com.devmozip.backend.service;

import com.devmozip.backend.dto.profile.ProfileReviewsResponse;

public interface ProfileReviewsService {
	ProfileReviewsResponse own(String email,int page,int size);
	ProfileReviewsResponse publicList(String slug,int page,int size);
}
