package com.devmozip.backend.service;

import com.devmozip.backend.dto.follow.FollowPageResponse;
import com.devmozip.backend.dto.follow.FollowStatusResponse;

/** 팔로우 상태(추가 API)와 팔로워(9.3)·팔로잉(9.4) 목록. 비로그인도 조회할 수 있다. */
public interface FollowQueryService {
	FollowStatusResponse status(String slug);
	FollowPageResponse followers(String slug, String query, boolean availableOnly, int page, int size);
	FollowPageResponse following(String slug, String query, boolean availableOnly, int page, int size);
}
