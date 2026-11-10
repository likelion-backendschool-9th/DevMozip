package com.devmozip.backend.entity.enums;

// 게시글 공개 범위 (docs/feed-plan.md 1절). HIDDEN·RESTRICTED 글은 피드에 나오지 않고 작성자만 상세를 볼 수 있다.
public enum PostVisibility {
	VISIBLE,    // 공개
	HIDDEN,     // 작성자 숨김 (WBS 3.8)
	RESTRICTED  // 관리자 숨김 (WBS 14.7). 작성자가 바꿀 수 없다
}
