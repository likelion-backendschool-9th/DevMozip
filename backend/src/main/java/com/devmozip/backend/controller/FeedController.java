package com.devmozip.backend.controller;

import java.util.List;

import com.devmozip.backend.dto.feed.CommentRequest;
import com.devmozip.backend.dto.feed.CommentResponse;
import com.devmozip.backend.dto.feed.FeedBookmarkResponse;
import com.devmozip.backend.dto.feed.FeedLikeResponse;
import com.devmozip.backend.dto.feed.FeedPageResponse;
import com.devmozip.backend.dto.feed.FeedPostRequest;
import com.devmozip.backend.dto.feed.FeedPostResponse;
import com.devmozip.backend.dto.feed.FeedReportRequest;
import com.devmozip.backend.dto.feed.FeedReportResponse;
import com.devmozip.backend.dto.feed.FeedRepostRequest;
import com.devmozip.backend.service.CommentService;
import com.devmozip.backend.service.FeedQueryService;
import com.devmozip.backend.service.FeedQueryService.ListRequest;
import com.devmozip.backend.service.FeedReactionService;
import com.devmozip.backend.service.FeedReportService;
import com.devmozip.backend.service.FeedService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * WBS 8.1~8.10, 8.13, 8.15 (docs/feed-plan.md 4절). 요청자는 Service가 인증 정보에서 찾는다.
 * 목록·상세·댓글 목록은 공개이고, 팔로잉 목록과 쓰기 요청은 로그인이 필요하다(SecurityConfig).
 * 작성·수정은 multipart/form-data이며 글 내용은 JSON 파트 "post", 이미지는 파일 파트 "images"(0~3개)다.
 */
@RestController
@RequestMapping("/api/v1/feeds")
public class FeedController {
	private static final String ID = "/{id:[0-9]{1,18}}";

	private final FeedService feeds;
	private final FeedQueryService queries;
	private final FeedReactionService reactions;
	private final CommentService comments;
	private final FeedReportService reports;

	public FeedController(FeedService feeds, FeedQueryService queries, FeedReactionService reactions,
		CommentService comments, FeedReportService reports) {
		this.feeds = feeds;
		this.queries = queries;
		this.reactions = reactions;
		this.comments = comments;
		this.reports = reports;
	}

	@GetMapping
	public FeedPageResponse all(@RequestParam(required = false) String category, @RequestParam(required = false) String q,
		@RequestParam(required = false) String sort, @RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "0") int seed) {
		return queries.all(new ListRequest(category, q, sort, page, size, seed));
	}

	@GetMapping("/recommands")
	public FeedPageResponse recommended(@RequestParam(required = false) String category, @RequestParam(required = false) String q,
		@RequestParam(required = false) String sort, @RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "0") int seed) {
		return queries.recommended(new ListRequest(category, q, sort, page, size, seed));
	}

	@GetMapping("/following")
	public FeedPageResponse following(@RequestParam(required = false) String category, @RequestParam(required = false) String q,
		@RequestParam(required = false) String sort, @RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "0") int seed) {
		return queries.following(new ListRequest(category, q, sort, page, size, seed));
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public FeedPostResponse create(@RequestPart("post") FeedPostRequest request,
		@RequestPart(name = "images", required = false) List<MultipartFile> images) {
		return feeds.create(request, images);
	}

	@GetMapping(ID)
	public FeedPostResponse detail(@PathVariable long id, @RequestParam(defaultValue = "false") boolean view) {
		return queries.detail(id, view);
	}

	@PutMapping(value = ID, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public FeedPostResponse update(@PathVariable long id, @RequestPart("post") FeedPostRequest request,
		@RequestPart(name = "images", required = false) List<MultipartFile> images) {
		return feeds.update(id, request, images);
	}

	// 논리 삭제라서 DELETE가 아니라 POST를 쓴다 (docs/feed-plan.md 1절).
	@PostMapping(ID + "/delete")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long id) {
		feeds.delete(id);
	}

	@PatchMapping(ID + "/likes")
	public FeedLikeResponse toggleLike(@PathVariable long id) {
		return reactions.toggleLike(id);
	}

	@PostMapping(ID + "/bookmark")
	public FeedBookmarkResponse bookmark(@PathVariable long id) {
		return reactions.bookmark(id);
	}

	@DeleteMapping(ID + "/bookmark")
	public FeedBookmarkResponse unbookmark(@PathVariable long id) {
		return reactions.unbookmark(id);
	}

	@GetMapping(ID + "/comments")
	public List<CommentResponse> comments(@PathVariable long id) {
		return comments.list(id);
	}

	@PostMapping(ID + "/comments")
	@ResponseStatus(HttpStatus.CREATED)
	public CommentResponse createComment(@PathVariable long id, @RequestBody CommentRequest request) {
		return comments.create(id, request.content());
	}

	@PostMapping(ID + "/reposts")
	@ResponseStatus(HttpStatus.CREATED)
	public FeedPostResponse repost(@PathVariable long id, @RequestBody(required = false) FeedRepostRequest request) {
		return feeds.repost(id, request == null ? null : request.content());
	}

	@DeleteMapping(ID + "/reposts")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancelRepost(@PathVariable long id) {
		feeds.cancelRepost(id);
	}

	@PostMapping(ID + "/reports")
	@ResponseStatus(HttpStatus.CREATED)
	public FeedReportResponse report(@PathVariable long id, @RequestBody FeedReportRequest request) {
		return reports.reportPost(id, request.reason());
	}
}
