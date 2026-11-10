package com.devmozip.backend.service;

import java.util.List;

import com.devmozip.backend.dto.feed.CommentResponse;

/** 게시글 댓글 (WBS 8.9~8.12). 요청자는 인증 정보에서 찾는다. */
public interface CommentService {
	/** 8.9 볼 수 있는 게시글의 댓글을 작성 순서대로 돌려준다. */
	List<CommentResponse> list(long postId);

	/** 8.10 */
	CommentResponse create(long postId, String content);

	/** 8.11 작성자만 수정한다. */
	CommentResponse update(long commentId, String content);

	/** 8.12 작성자만 삭제한다(논리 삭제). */
	void delete(long commentId);
}
