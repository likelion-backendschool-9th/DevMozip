package com.devmozip.backend.dto.follow;

/** 팔로우·언팔로우(POST·DELETE /api/v1/users/{slug}/follow) 후의 최종 상태. followerCount는 대상의 팔로워 수다. */
public record FollowResultResponse(boolean followed, boolean activityNotificationEnabled, long followerCount) {}
