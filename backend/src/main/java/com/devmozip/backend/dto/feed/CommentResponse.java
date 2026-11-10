package com.devmozip.backend.dto.feed;

import java.time.OffsetDateTime;

/** 댓글(8.9~8.11). mine·reported는 요청자 기준이며 비로그인이면 false다. 시각은 한국 시각(+09:00)이다. */
public record CommentResponse(
	long id,
	long postId,
	String content,
	FeedAuthorResponse author,
	OffsetDateTime createdAt,
	OffsetDateTime updatedAt,
	boolean edited,
	boolean mine,
	boolean reported
) {}
