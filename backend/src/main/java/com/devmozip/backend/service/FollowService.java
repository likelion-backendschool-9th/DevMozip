package com.devmozip.backend.service;

import com.devmozip.backend.dto.follow.FollowNotificationResponse;
import com.devmozip.backend.dto.follow.FollowResultResponse;

/** 팔로우(9.1)·언팔로우(9.2)·알림 설정(9.5). 요청자는 로그인한 본인이다. */
public interface FollowService {
	FollowResultResponse follow(String slug);
	FollowResultResponse unfollow(String slug);
	FollowNotificationResponse updateNotification(String slug, boolean activityNotificationEnabled);
}
