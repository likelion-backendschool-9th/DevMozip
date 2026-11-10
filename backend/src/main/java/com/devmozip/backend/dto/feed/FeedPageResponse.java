package com.devmozip.backend.dto.feed;

import java.util.List;

/** 피드 목록 한 페이지. 화면은 hasNext가 true일 때 page + 1을 요청해 기존 목록 뒤에 붙인다. */
public record FeedPageResponse(List<FeedPostResponse> items, int page, int size, boolean hasNext, long totalCount) {}
