package com.devmozip.backend.service;

import com.devmozip.backend.dto.feed.FeedBookmarkResponse;
import com.devmozip.backend.dto.feed.FeedLikeResponse;

/** 게시글 좋아요·북마크 (WBS 8.7, 8.8). 공통 likes·bookmarks의 post_id를 쓴다. */
public interface FeedReactionService {
	/** 8.7 좋아요와 좋아요 취소를 오간다. 응답은 처리 뒤의 최종 상태다. */
	FeedLikeResponse toggleLike(long postId);

	/** 8.8 북마크 추가. 이미 있으면 그대로 둔다. */
	FeedBookmarkResponse bookmark(long postId);

	/** 8.8 북마크 해제. 게시글이 삭제·숨김이어도 내 북마크는 지울 수 있다. */
	FeedBookmarkResponse unbookmark(long postId);
}
