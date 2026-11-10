package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;

import com.devmozip.backend.dto.feed.FeedReportResponse;
import com.devmozip.backend.entity.Comment;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Post;
import com.devmozip.backend.entity.Report;
import com.devmozip.backend.repository.CommentRepository;
import com.devmozip.backend.repository.PostRepository;
import com.devmozip.backend.repository.ReportRepository;
import com.devmozip.backend.security.member.CurrentMemberProvider;
import com.devmozip.backend.service.FeedPostAssembler;
import com.devmozip.backend.service.FeedReportService;
import com.devmozip.backend.service.FeedRules;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 신고는 reports에 대상 id만 채우고 reported_user_id는 비운다(docs/feed-plan.md 4절).
 * 대상 행을 잠근 뒤 중복을 확인해 같은 계정의 동시 신고가 두 건 저장되지 않게 한다.
 */
@Service
@Transactional
public class FeedReportServiceImpl implements FeedReportService {
	private final PostRepository posts;
	private final CommentRepository comments;
	private final ReportRepository reports;
	private final CurrentMemberProvider currentMember;
	private final Clock clock;

	public FeedReportServiceImpl(PostRepository posts, CommentRepository comments, ReportRepository reports,
		CurrentMemberProvider currentMember, @Qualifier("feedClock") Clock clock) {
		this.posts = posts;
		this.comments = comments;
		this.reports = reports;
		this.currentMember = currentMember;
		this.clock = clock;
	}

	@Override
	public FeedReportResponse reportPost(long postId, String reason) {
		Member reporter = currentMember.requireActive();
		String label = FeedRules.reportReason(reason);
		Post post = posts.findLiveForUpdate(postId).filter(found -> FeedRules.readable(found, reporter.getId()))
			.orElseThrow(FeedRules::postNotFound);
		if (post.isWrittenBy(reporter.getId())) throw ownContent();
		if (reports.existsByReporter_IdAndPostIdAndCommentIdIsNull(reporter.getId(), postId)) throw duplicate();
		Report report = reports.saveAndFlush(Report.ofPost(reporter, postId, label, LocalDateTime.now(clock)));
		return response(report, "POST", postId);
	}

	@Override
	public FeedReportResponse reportComment(long commentId, String reason) {
		Member reporter = currentMember.requireActive();
		String label = FeedRules.reportReason(reason);
		Comment comment = comments.findLiveForUpdate(commentId)
			.filter(found -> FeedRules.readable(found.getPost(), reporter.getId()))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."));
		if (comment.isWrittenBy(reporter.getId())) throw ownContent();
		if (reports.existsByReporter_IdAndCommentId(reporter.getId(), commentId)) throw duplicate();
		Report report = reports.saveAndFlush(Report.ofComment(reporter, commentId, label, LocalDateTime.now(clock)));
		return response(report, "COMMENT", commentId);
	}

	private static FeedReportResponse response(Report report, String targetType, long targetId) {
		return new FeedReportResponse(report.getId(), targetType, targetId, report.getStatus().name(),
			FeedPostAssembler.atSeoul(report.getCreatedAt()));
	}

	private static ResponseStatusException ownContent() {
		return FeedRules.badRequest("본인이 작성한 내용은 신고할 수 없습니다.");
	}

	private static ResponseStatusException duplicate() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "이미 신고한 내용입니다.");
	}
}
