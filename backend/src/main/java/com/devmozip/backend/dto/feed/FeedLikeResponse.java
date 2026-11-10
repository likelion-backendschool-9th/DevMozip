package com.devmozip.backend.dto.feed;

/** 좋아요 토글(8.7) 뒤의 최종 상태. 화면은 이 값으로 버튼과 수를 맞춘다. */
public record FeedLikeResponse(long postId, boolean liked, long likeCount) {}
