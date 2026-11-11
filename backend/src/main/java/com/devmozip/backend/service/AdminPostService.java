package com.devmozip.backend.service;

import com.devmozip.backend.dto.admin.AdminActionRequest;
import com.devmozip.backend.dto.admin.AdminPageResponse;
import com.devmozip.backend.dto.admin.AdminPostResponse;
import com.devmozip.backend.dto.admin.AdminRestrictRequest;

/**
 * WBS 14.6 게시글·댓글 관리, 14.7 표시 여부 (docs/admin-plan.md 5.6, D3·D4).
 * target은 POST(기본)·COMMENT다. 댓글은 숨김 상태가 없어 삭제만 한다.
 */
public interface AdminPostService {
	/** POST면 AdminPostResponse, COMMENT면 AdminCommentResponse 목록이다. visibility는 게시글에만 쓴다. */
	AdminPageResponse<?> list(String target, String q, String visibility, int page, int size);

	/** 관리자 삭제. 뒤처리는 작성자 삭제(8.6·8.12)와 같다. */
	void act(long id, String target, AdminActionRequest request);

	/** 게시글 관리자 숨김·복원. 댓글이면 400이다. */
	AdminPostResponse restrict(long id, String target, AdminRestrictRequest request);
}
