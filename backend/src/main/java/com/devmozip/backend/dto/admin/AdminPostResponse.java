package com.devmozip.backend.dto.admin;

import java.time.LocalDateTime;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Post;

/**
 * 관리자 게시글 목록 항목 (WBS 14.6). 퍼간 글(REPOST)은 제목이 없고 content가 의견이다.
 * 작성자가 탈퇴했으면 authorTag가 null이다.
 */
public record AdminPostResponse(long id, String type, String title, String content, String authorTag, String authorNickname,
	String visibility, LocalDateTime createdAt) {

	public static AdminPostResponse of(Post post) {
		Member author = post.getAuthor();
		return new AdminPostResponse(post.getId(), post.getType().name(), post.getTitle(), post.getContent(), tagOf(author),
			author.getNickname(), post.getVisibility().name(), post.getCreatedAt());
	}

	static String tagOf(Member member) {
		return member == null || member.getTag() == null || member.getTag().isBlank() ? null : member.getTag();
	}
}
