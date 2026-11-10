package com.devmozip.backend.dto.feed;

/** 북마크 추가·해제(8.8) 뒤의 최종 상태 */
public record FeedBookmarkResponse(long postId, boolean bookmarked) {}
