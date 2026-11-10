package com.devmozip.backend.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.devmozip.backend.dto.feed.FeedAuthorResponse;
import com.devmozip.backend.dto.feed.FeedMyRepostResponse;
import com.devmozip.backend.dto.feed.FeedOriginalResponse;
import com.devmozip.backend.dto.feed.FeedPostResponse;
import com.devmozip.backend.entity.Post;
import com.devmozip.backend.repository.BookmarkRepository;
import com.devmozip.backend.repository.CommentRepository;
import com.devmozip.backend.repository.LikeRepository;
import com.devmozip.backend.repository.PostRepository;
import com.devmozip.backend.repository.ReportRepository;

import org.springframework.stereotype.Component;

/** 게시글 응답을 만든다. 한 페이지의 좋아요·댓글 수와 요청자 기준 값은 게시글마다가 아니라 한 번에 읽는다. */
@Component
public class FeedPostAssembler {
	private static final ZoneOffset SEOUL = ZoneOffset.ofHours(9);

	private final PostRepository posts;
	private final LikeRepository likes;
	private final BookmarkRepository bookmarks;
	private final CommentRepository comments;
	private final ReportRepository reports;

	public FeedPostAssembler(PostRepository posts, LikeRepository likes, BookmarkRepository bookmarks,
		CommentRepository comments, ReportRepository reports) {
		this.posts = posts;
		this.likes = likes;
		this.bookmarks = bookmarks;
		this.comments = comments;
		this.reports = reports;
	}

	public FeedPostResponse toResponse(Post post, Long viewerId) {
		return toResponses(List.of(post), viewerId).get(0);
	}

	/** 순서는 받은 목록 순서를 따른다. 트랜잭션 안에서 불러야 작성자·원본을 읽을 수 있다. */
	public List<FeedPostResponse> toResponses(List<Post> page, Long viewerId) {
		if (page.isEmpty()) return List.of();
		List<Long> ids = page.stream().map(Post::getId).toList();
		Map<Long, Long> likeCounts = counts(likes.countByPosts(ids));
		Map<Long, Long> commentCounts = counts(comments.countLiveByPosts(ids));
		Set<Long> liked = viewerId == null ? Set.of() : new HashSet<>(likes.likedPostIds(viewerId, ids));
		Set<Long> bookmarked = viewerId == null ? Set.of() : new HashSet<>(bookmarks.bookmarkedPostIds(viewerId, ids));
		Set<Long> reported = viewerId == null ? Set.of() : new HashSet<>(reports.reportedPostIds(viewerId, ids));
		Map<Long, Post> myReposts = viewerId == null ? Map.of() : myReposts(viewerId, page);
		return page.stream().map(post -> {
			Post mine = myReposts.get(post.getId());
			return new FeedPostResponse(post.getId(), post.getType(), post.getCategory(), post.getTitle(), post.getContent(),
				post.getTechStacks(), post.getInterests(), post.getImageUrls(), post.getViewCount(),
				likeCounts.getOrDefault(post.getId(), 0L), commentCounts.getOrDefault(post.getId(), 0L), post.getVisibility(),
				FeedAuthorResponse.from(post.getAuthor()), post.isRepost() ? original(post.getOriginalPost()) : null,
				atSeoul(post.getCreatedAt()), atSeoul(post.getUpdatedAt()), post.isEdited(),
				viewerId != null && post.isWrittenBy(viewerId), liked.contains(post.getId()), bookmarked.contains(post.getId()),
				reported.contains(post.getId()), mine == null ? null : new FeedMyRepostResponse(mine.getId(), mine.getContent()));
		}).toList();
	}

	public static OffsetDateTime atSeoul(LocalDateTime value) {
		return value == null ? null : value.atOffset(SEOUL);
	}

	private Map<Long, Post> myReposts(long viewerId, List<Post> page) {
		Collection<Long> originals = page.stream().filter(post -> !post.isRepost()).map(Post::getId).toList();
		if (originals.isEmpty()) return Map.of();
		Map<Long, Post> result = new HashMap<>();
		for (Post repost : posts.findLiveRepostsOf(viewerId, originals)) result.put(repost.getOriginalPost().getId(), repost);
		return result;
	}

	private static FeedOriginalResponse original(Post original) {
		if (original == null) return null;
		if (!FeedRules.originalAvailable(original)) return FeedOriginalResponse.unavailable(original.getId());
		return new FeedOriginalResponse(original.getId(), true, original.getCategory(), original.getTitle(),
			FeedAuthorResponse.from(original.getAuthor()));
	}

	private static Map<Long, Long> counts(List<Object[]> rows) {
		Map<Long, Long> result = new HashMap<>();
		for (Object[] row : rows) result.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
		return result;
	}
}
