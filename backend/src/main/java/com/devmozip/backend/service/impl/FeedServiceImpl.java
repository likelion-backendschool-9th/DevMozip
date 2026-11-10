package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.devmozip.backend.dto.feed.FeedPostRequest;
import com.devmozip.backend.dto.feed.FeedPostResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Post;
import com.devmozip.backend.repository.PostRepository;
import com.devmozip.backend.security.member.CurrentMemberProvider;
import com.devmozip.backend.service.FeedPostAssembler;
import com.devmozip.backend.service.FeedRules;
import com.devmozip.backend.service.FeedService;
import com.devmozip.backend.storage.FeedImageStorage;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 같은 게시글의 수정·삭제·퍼가기는 게시글 행을 잠가 차례로 처리한다.
 * 이미지 파일은 DB 저장이 되돌려지면 지우고, 빠진 기존 파일은 커밋 뒤에 지운다(FeedImageStorage).
 */
@Service
@Transactional
public class FeedServiceImpl implements FeedService {
	private final PostRepository posts;
	private final FeedImageStorage images;
	private final FeedPostAssembler assembler;
	private final CurrentMemberProvider currentMember;
	private final Clock clock;

	public FeedServiceImpl(PostRepository posts, FeedImageStorage images, FeedPostAssembler assembler,
		CurrentMemberProvider currentMember, @Qualifier("feedClock") Clock clock) {
		this.posts = posts;
		this.images = images;
		this.assembler = assembler;
		this.currentMember = currentMember;
		this.clock = clock;
	}

	@Override
	public FeedPostResponse create(FeedPostRequest request, List<MultipartFile> files) {
		Member author = currentMember.requireActive();
		FeedRules.PostInput input = FeedRules.post(request);
		if (request.keepImageUrls() != null && !request.keepImageUrls().isEmpty()) throw invalidImages();
		if (count(files) > FeedImageStorage.MAX_COUNT) throw invalidImages();
		// 파일을 먼저 확인해 잘못된 파일이면 게시글을 만들지 않는다.
		List<FeedImageStorage.Upload> uploads = images.read(files);
		LocalDateTime now = LocalDateTime.now(clock);
		// 파일 이름에 게시글 id가 들어가므로 게시글을 먼저 저장해 id를 받는다.
		Post post = posts.saveAndFlush(Post.write(author, input.category(), input.title(), input.content(),
			input.techStacks(), input.interests(), now));
		if (!uploads.isEmpty()) post.replaceImageUrls(images.store(post.getId(), uploads, 1, now));
		return assembler.toResponse(post, author.getId());
	}

	@Override
	public FeedPostResponse update(long id, FeedPostRequest request, List<MultipartFile> files) {
		Member author = currentMember.requireActive();
		Post post = ownPost(id, author, "본인 게시글만 수정할 수 있습니다.");
		LocalDateTime now = LocalDateTime.now(clock);
		if (post.isRepost()) {
			if (count(files) > 0) throw FeedRules.badRequest("퍼간 글은 의견만 수정할 수 있습니다.");
			post.editOpinion(FeedRules.opinion(request == null ? null : request.content()), now);
			return assembler.toResponse(post, author.getId());
		}
		FeedRules.PostInput input = FeedRules.post(request);
		List<String> keep = keptImages(post, request.keepImageUrls());
		if (keep.size() + count(files) > FeedImageStorage.MAX_COUNT) throw invalidImages();
		List<FeedImageStorage.Upload> uploads = images.read(files);
		// 남긴 이미지는 파일 이름을 바꾸지 않고, 새 이미지는 남긴 이미지 뒤의 순서로 저장한다.
		List<String> urls = new ArrayList<>(keep);
		urls.addAll(images.store(post.getId(), uploads, keep.size() + 1, now));
		List<String> removed = post.getImageUrls().stream().filter(url -> !keep.contains(url)).toList();
		post.edit(input.category(), input.title(), input.content(), input.techStacks(), input.interests(), urls, now);
		images.deleteAfterCommit(post.getId(), removed);
		return assembler.toResponse(post, author.getId());
	}

	@Override
	public void delete(long id) {
		Member author = currentMember.requireActive();
		Post post = ownPost(id, author, "본인 게시글만 삭제할 수 있습니다.");
		post.softDelete(LocalDateTime.now(clock));
		images.deleteAfterCommit(post.getId(), post.getImageUrls());
	}

	@Override
	public FeedPostResponse repost(long originalId, String opinion) {
		Member member = currentMember.requireActive();
		String content = FeedRules.opinion(opinion);
		// 같은 사람의 동시 퍼가기가 둘 다 저장되지 않도록 원본 행을 잠근 뒤 확인한다.
		Post original = posts.findLiveForUpdate(originalId)
			.filter(post -> !post.isRepost() && FeedRules.originalAvailable(post))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "원본 글을 찾을 수 없습니다."));
		if (!posts.findLiveReposts(member.getId(), originalId).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 내 프로필에 퍼간 글입니다.");
		}
		Post repost = posts.saveAndFlush(Post.repost(member, original, content, LocalDateTime.now(clock)));
		return assembler.toResponse(repost, member.getId());
	}

	@Override
	public void cancelRepost(long originalId) {
		Member member = currentMember.requireActive();
		LocalDateTime now = LocalDateTime.now(clock);
		// 원본이 삭제·숨김이어도 내가 퍼간 글은 정리할 수 있다.
		posts.findLiveReposts(member.getId(), originalId).forEach(repost -> repost.softDelete(now));
	}

	/** 잠근 게시글. 없거나 삭제됐으면 404, 작성자가 아니면 403 */
	private Post ownPost(long id, Member author, String forbidden) {
		Post post = posts.findLiveForUpdate(id).orElseThrow(FeedRules::postNotFound);
		if (!post.isWrittenBy(author.getId())) {
			// 다른 사람의 숨긴 글은 있는지도 알리지 않는다.
			if (!FeedRules.readable(post, author.getId())) throw FeedRules.postNotFound();
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbidden);
		}
		return post;
	}

	/** 남길 기존 이미지. 지금 이 게시글의 이미지여야 하고 중복이 없어야 한다. */
	private static List<String> keptImages(Post post, List<String> requested) {
		if (requested == null) return List.of();
		Set<String> seen = new HashSet<>();
		for (String url : requested) {
			if (url == null || !post.getImageUrls().contains(url) || !seen.add(url)) throw FeedRules.badRequest("이미지 정보를 확인해 주세요.");
		}
		return List.copyOf(requested);
	}

	private static int count(List<MultipartFile> files) {
		return files == null ? 0 : files.size();
	}

	private static ResponseStatusException invalidImages() {
		return FeedRules.badRequest(FeedImageStorage.INVALID_MESSAGE);
	}
}
