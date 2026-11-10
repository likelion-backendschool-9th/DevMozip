package com.devmozip.backend.dto.feed;

import com.devmozip.backend.entity.enums.PostCategory;

/**
 * 퍼간 글의 원본 요약. 원본이 삭제·숨김이거나 작성자가 이용 정지면 available=false이고 id 외의 값은 비운다.
 * 화면은 이때 원본 삭제 안내를 표시한다.
 */
public record FeedOriginalResponse(long id, boolean available, PostCategory category, String title, FeedAuthorResponse author) {
	public static FeedOriginalResponse unavailable(long id) {
		return new FeedOriginalResponse(id, false, null, null, null);
	}
}
