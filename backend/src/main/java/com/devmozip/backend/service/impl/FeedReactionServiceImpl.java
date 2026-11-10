package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;

import com.devmozip.backend.dto.feed.FeedBookmarkResponse;
import com.devmozip.backend.dto.feed.FeedLikeResponse;
import com.devmozip.backend.entity.Bookmark;
import com.devmozip.backend.entity.Like;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Post;
import com.devmozip.backend.repository.BookmarkRepository;
import com.devmozip.backend.repository.LikeRepository;
import com.devmozip.backend.repository.PostRepository;
import com.devmozip.backend.security.member.CurrentMemberProvider;
import com.devmozip.backend.service.FeedReactionService;
import com.devmozip.backend.service.FeedRules;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 같은 게시글의 반응은 게시글 행을 잠가 차례로 처리하므로, 같은 요청이 동시에 와도 중복 행이 생기지 않는다. */
@Service
@Transactional
public class FeedReactionServiceImpl implements FeedReactionService {
	private final PostRepository posts;
	private final LikeRepository likes;
	private final BookmarkRepository bookmarks;
	private final CurrentMemberProvider currentMember;
	private final Clock clock;

	public FeedReactionServiceImpl(PostRepository posts, LikeRepository likes, BookmarkRepository bookmarks,
		CurrentMemberProvider currentMember, @Qualifier("feedClock") Clock clock) {
		this.posts = posts;
		this.likes = likes;
		this.bookmarks = bookmarks;
		this.currentMember = currentMember;
		this.clock = clock;
	}

	@Override
	public FeedLikeResponse toggleLike(long postId) {
		Member member = currentMember.requireActive();
		readableForUpdate(postId, member);
		boolean liked;
		if (likes.existsByUserIdAndPostId(member.getId(), postId)) {
			likes.removePost(member.getId(), postId);
			liked = false;
		} else {
			likes.saveAndFlush(Like.forPost(member.getId(), postId, LocalDateTime.now(clock)));
			liked = true;
		}
		return new FeedLikeResponse(postId, liked, likes.countByPostId(postId));
	}

	@Override
	public FeedBookmarkResponse bookmark(long postId) {
		Member member = currentMember.requireActive();
		readableForUpdate(postId, member);
		if (!bookmarks.existsByUserIdAndPostId(member.getId(), postId)) {
			bookmarks.saveAndFlush(Bookmark.forPost(member.getId(), postId, LocalDateTime.now(clock)));
		}
		return new FeedBookmarkResponse(postId, true);
	}

	@Override
	public FeedBookmarkResponse unbookmark(long postId) {
		Member member = currentMember.requireActive();
		bookmarks.removePost(member.getId(), postId);
		return new FeedBookmarkResponse(postId, false);
	}

	private Post readableForUpdate(long postId, Member member) {
		return posts.findLiveForUpdate(postId).filter(post -> FeedRules.readable(post, member.getId()))
			.orElseThrow(FeedRules::postNotFound);
	}
}
