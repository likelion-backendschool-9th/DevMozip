package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.devmozip.backend.dto.feed.CommentResponse;
import com.devmozip.backend.dto.feed.FeedAuthorResponse;
import com.devmozip.backend.entity.Comment;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Post;
import com.devmozip.backend.repository.CommentRepository;
import com.devmozip.backend.repository.PostRepository;
import com.devmozip.backend.repository.ReportRepository;
import com.devmozip.backend.security.member.CurrentMemberProvider;
import com.devmozip.backend.service.CommentService;
import com.devmozip.backend.service.FeedPostAssembler;
import com.devmozip.backend.service.FeedRules;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 댓글은 볼 수 있는 게시글(FeedRules.readable)에서만 읽고 쓴다. 삭제된 게시글의 댓글은 404다. */
@Service
@Transactional
public class CommentServiceImpl implements CommentService {
	private final PostRepository posts;
	private final CommentRepository comments;
	private final ReportRepository reports;
	private final CurrentMemberProvider currentMember;
	private final Clock clock;

	public CommentServiceImpl(PostRepository posts, CommentRepository comments, ReportRepository reports,
		CurrentMemberProvider currentMember, @Qualifier("feedClock") Clock clock) {
		this.posts = posts;
		this.comments = comments;
		this.reports = reports;
		this.currentMember = currentMember;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<CommentResponse> list(long postId) {
		Long viewerId = currentMember.find().map(Member::getId).orElse(null);
		readablePost(postId, viewerId);
		List<Comment> found = comments.findLiveByPost(postId);
		Set<Long> reported = viewerId == null || found.isEmpty() ? Set.of()
			: new HashSet<>(reports.reportedCommentIds(viewerId, found.stream().map(Comment::getId).toList()));
		return found.stream().map(comment -> response(comment, viewerId, reported.contains(comment.getId()))).toList();
	}

	@Override
	public CommentResponse create(long postId, String content) {
		Member author = currentMember.requireActive();
		String text = FeedRules.comment(content);
		Post post = readablePost(postId, author.getId());
		Comment comment = comments.saveAndFlush(new Comment(post, author, text, LocalDateTime.now(clock)));
		return response(comment, author.getId(), false);
	}

	@Override
	public CommentResponse update(long commentId, String content) {
		Member author = currentMember.requireActive();
		String text = FeedRules.comment(content);
		Comment comment = ownComment(commentId, author);
		comment.edit(text, LocalDateTime.now(clock));
		return response(comment, author.getId(), false);
	}

	@Override
	public void delete(long commentId) {
		Member author = currentMember.requireActive();
		ownComment(commentId, author).softDelete(LocalDateTime.now(clock));
	}

	private Post readablePost(long postId, Long viewerId) {
		return posts.findLive(postId).filter(post -> FeedRules.readable(post, viewerId)).orElseThrow(FeedRules::postNotFound);
	}

	/** 잠근 댓글. 댓글이나 게시글을 볼 수 없으면 404, 작성자가 아니면 403 */
	private Comment ownComment(long commentId, Member author) {
		Comment comment = comments.findLiveForUpdate(commentId)
			.filter(found -> FeedRules.readable(found.getPost(), author.getId()))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."));
		if (!comment.isWrittenBy(author.getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 댓글만 관리할 수 있습니다.");
		return comment;
	}

	private static CommentResponse response(Comment comment, Long viewerId, boolean reported) {
		return new CommentResponse(comment.getId(), comment.getPost().getId(), comment.getContent(),
			FeedAuthorResponse.from(comment.getAuthor()), FeedPostAssembler.atSeoul(comment.getCreatedAt()),
			FeedPostAssembler.atSeoul(comment.getUpdatedAt()), comment.isEdited(),
			viewerId != null && comment.isWrittenBy(viewerId), reported);
	}
}
