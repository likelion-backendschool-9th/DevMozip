package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import com.devmozip.backend.dto.admin.AdminActionRequest;
import com.devmozip.backend.dto.admin.AdminCommentResponse;
import com.devmozip.backend.dto.admin.AdminPageResponse;
import com.devmozip.backend.dto.admin.AdminPostResponse;
import com.devmozip.backend.dto.admin.AdminRestrictRequest;
import com.devmozip.backend.entity.Comment;
import com.devmozip.backend.entity.Post;
import com.devmozip.backend.entity.enums.PostVisibility;
import com.devmozip.backend.repository.AdminPostQueryRepository;
import com.devmozip.backend.repository.CommentRepository;
import com.devmozip.backend.repository.PostRepository;
import com.devmozip.backend.security.admin.AdminAccess;
import com.devmozip.backend.service.AdminPaging;
import com.devmozip.backend.service.AdminPostService;
import com.devmozip.backend.storage.FeedImageStorage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관리자 게시글·댓글 관리 (docs/admin-plan.md 5.6). 대상 행을 잠그고 바꾼다.
 * 게시글 삭제는 작성자 삭제(FeedServiceImpl.delete)처럼 논리 삭제하고 커밋 뒤 이미지 파일을 지운다.
 * 퍼간 글은 원본이 삭제되면 기존 피드 규칙(FeedRules.originalAvailable)대로 원본 안내를 보여 준다.
 */
@Service
public class AdminPostServiceImpl implements AdminPostService {
	static final String POST = "POST";
	static final String COMMENT = "COMMENT";

	private final AdminAccess access;
	private final AdminPostQueryRepository query;
	private final PostRepository posts;
	private final CommentRepository comments;
	private final FeedImageStorage images;
	private final Clock clock;

	public AdminPostServiceImpl(AdminAccess access, AdminPostQueryRepository query, PostRepository posts, CommentRepository comments,
		FeedImageStorage images, @Qualifier("feedClock") Clock clock) {
		this.access = access;
		this.query = query;
		this.posts = posts;
		this.comments = comments;
		this.images = images;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public AdminPageResponse<?> list(String target, String q, String visibility, int page, int size) {
		access.require();
		String keyword = AdminPaging.keyword(q);
		if (isComment(target)) {
			if (visibility != null && !visibility.isBlank()) throw badRequest("댓글은 공개 범위로 거를 수 없습니다.");
			return AdminPageResponse.of(query.searchComments(keyword, AdminPaging.request(page, size)), AdminCommentResponse::of);
		}
		PostVisibility filter = AdminPaging.option(PostVisibility.class, visibility, "공개 범위를 확인해 주세요.");
		return AdminPageResponse.of(query.searchPosts(keyword, filter, AdminPaging.request(page, size)), AdminPostResponse::of);
	}

	@Override
	@Transactional
	public void act(long id, String target, AdminActionRequest request) {
		access.require();
		if (!request.isDelete()) throw badRequest("허용하지 않는 조치입니다.");
		LocalDateTime now = LocalDateTime.now(clock);
		if (isComment(target)) {
			Comment comment = comments.findLiveForUpdate(id).orElseThrow(() -> notFound("댓글을 찾을 수 없습니다."));
			comment.softDelete(now);
			return;
		}
		Post post = posts.findLiveForUpdate(id).orElseThrow(() -> notFound("게시글을 찾을 수 없습니다."));
		post.softDelete(now);
		images.deleteAfterCommit(post.getId(), post.getImageUrls());
	}

	@Override
	@Transactional
	public AdminPostResponse restrict(long id, String target, AdminRestrictRequest request) {
		access.require();
		if (isComment(target)) throw badRequest("댓글은 숨김 상태가 없습니다. 삭제를 이용해 주세요.");
		Post post = posts.findLiveForUpdate(id).orElseThrow(() -> notFound("게시글을 찾을 수 없습니다."));
		post.restrictByAdmin(request.restricted());
		return AdminPostResponse.of(post);
	}

	/** target은 POST(기본값)·COMMENT만 받는다. */
	private static boolean isComment(String target) {
		if (target == null || target.isBlank() || POST.equals(target)) return false;
		if (COMMENT.equals(target)) return true;
		throw badRequest("대상을 확인해 주세요.");
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	private static ResponseStatusException notFound(String message) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
	}
}
