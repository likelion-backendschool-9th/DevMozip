package com.devmozip.backend.dto.follow;

/**
 * GET /api/v1/users/{slug}/follow 응답. 수는 대상 사용자 기준이고,
 * followed·activityNotificationEnabled는 요청자 기준이다(비로그인이거나 본인 프로필이면 false).
 */
public record FollowStatusResponse(long followerCount, long followingCount, boolean followed, boolean activityNotificationEnabled) {}
