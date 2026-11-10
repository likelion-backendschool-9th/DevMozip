package com.devmozip.backend.dto.admin;

import java.time.LocalDateTime;
import com.devmozip.backend.entity.Comment;

/** 관리자 댓글 목록 항목 (WBS 14.6). postTitle은 퍼간 글이면 null이다. */
public record AdminCommentResponse(long id, long postId, String postTitle, String content, String authorTag, String authorNickname,
	LocalDateTime createdAt) {

	public static AdminCommentResponse of(Comment comment) {
		return new AdminCommentResponse(comment.getId(), comment.getPost().getId(), comment.getPost().getTitle(), comment.getContent(),
			AdminPostResponse.tagOf(comment.getAuthor()), comment.getAuthor().getNickname(), comment.getCreatedAt());
	}
}
