package com.devmozip.backend.dto.follow;

import java.util.List;

/** 팔로워·팔로잉 목록 한 페이지. totalCount는 검색·필터를 적용한 전체 수다. */
public record FollowPageResponse(List<FollowUserResponse> items, int page, int size, boolean hasNext, long totalCount) {}
