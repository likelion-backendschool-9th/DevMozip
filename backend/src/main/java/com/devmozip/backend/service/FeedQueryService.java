package com.devmozip.backend.service;

import com.devmozip.backend.dto.feed.FeedPageResponse;
import com.devmozip.backend.dto.feed.FeedPostResponse;

/** 피드 목록·상세 (WBS 8.1~8.3, 8.5). 요청자는 인증 정보에서 찾는다. */
public interface FeedQueryService {
	/** 목록 조건. 문자열 값은 Service가 확인한다. sort가 없으면 목록마다 기본 정렬을 쓴다. */
	record ListRequest(String category, String q, String sort, int page, int size, int seed) {}

	/** 8.1 전체 피드. 기본 정렬은 최신순 */
	FeedPageResponse all(ListRequest request);

	/** 8.2 추천 피드. 초기에는 seed로 섞은 랜덤 추천이며 기본 정렬은 추천순 */
	FeedPageResponse recommended(ListRequest request);

	/** 8.2 팔로잉 피드. 로그인이 필요하고 기본 정렬은 최신순 */
	FeedPageResponse following(ListRequest request);

	/** 8.5 상세. view가 true면 작성자 본인이 아닐 때 조회수를 1 올린다. */
	FeedPostResponse detail(long id, boolean view);
}
