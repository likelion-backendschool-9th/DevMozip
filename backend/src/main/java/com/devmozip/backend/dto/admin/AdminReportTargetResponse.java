package com.devmozip.backend.dto.admin;

/**
 * 신고 대상 (WBS 14.8). user는 신고 대상 회원(MEMBER·USER)이거나 대상 글·댓글의 작성자(POST·COMMENT)이며, 공고 신고는 null이다.
 * content는 게시글 제목(퍼간 글은 의견)이나 댓글 내용이다. deleted는 대상 공고·글·댓글이 삭제됐는지다.
 */
public record AdminReportTargetResponse(Long projectId, String projectTitle, AdminPartyResponse user, Long postId, Long commentId,
	String content, boolean deleted) {
}
