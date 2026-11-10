package com.devmozip.backend.service;

import com.devmozip.backend.dto.feed.FeedReportResponse;

/** 게시글·댓글 신고 (WBS 8.15, 8.16). 같은 계정은 같은 대상을 한 번만 신고하고, 본인이 쓴 내용은 신고할 수 없다. */
public interface FeedReportService {
	FeedReportResponse reportPost(long postId, String reason);

	FeedReportResponse reportComment(long commentId, String reason);
}
