package com.devmozip.backend.dto.feed;

import java.time.OffsetDateTime;
import java.util.List;

import com.devmozip.backend.entity.enums.PostCategory;
import com.devmozip.backend.entity.enums.PostType;
import com.devmozip.backend.entity.enums.PostVisibility;

/**
 * 게시글 목록 항목과 상세(8.1~8.5). 시각은 한국 시각(+09:00)이다.
 * mine·liked·bookmarked·reported·myRepost는 요청자 기준이며 비로그인이면 false·null이다.
 * original은 퍼간 글(REPOST)만, myRepost는 원본 글(POST)만 채운다.
 */
public record FeedPostResponse(
	long id,
	PostType type,
	PostCategory category,
	String title,
	String content,
	List<String> techStacks,
	List<String> interests,
	List<String> imageUrls,
	long viewCount,
	long likeCount,
	long commentCount,
	PostVisibility visibility,
	FeedAuthorResponse author,
	FeedOriginalResponse original,
	OffsetDateTime createdAt,
	OffsetDateTime updatedAt,
	boolean edited,
	boolean mine,
	boolean liked,
	boolean bookmarked,
	boolean reported,
	FeedMyRepostResponse myRepost
) {}
