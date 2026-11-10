package com.devmozip.backend.entity.enums;

// 게시글 유형 (docs/feed-plan.md 1절). 퍼간 글은 원본 posts.id를 original_post_id에 둔다.
public enum PostType {
	POST,   // 원본 글
	REPOST  // 퍼온 글
}
