package com.devmozip.backend.service.impl;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.devmozip.backend.dto.feed.FeedPageResponse;
import com.devmozip.backend.dto.feed.FeedPostResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Post;
import com.devmozip.backend.repository.PostQueryRepository;
import com.devmozip.backend.repository.PostQueryRepository.Condition;
import com.devmozip.backend.repository.PostQueryRepository.Sort;
import com.devmozip.backend.repository.PostRepository;
import com.devmozip.backend.security.member.CurrentMemberProvider;
import com.devmozip.backend.service.FeedPostAssembler;
import com.devmozip.backend.service.FeedQueryService;
import com.devmozip.backend.service.FeedRules;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 목록의 id와 수·요청자 기준 값이 한 시점의 값이 되도록 REPEATABLE_READ로 읽는다. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class FeedQueryServiceImpl implements FeedQueryService {
	private final PostRepository posts;
	private final PostQueryRepository queries;
	private final FeedPostAssembler assembler;
	private final CurrentMemberProvider currentMember;

	public FeedQueryServiceImpl(PostRepository posts, PostQueryRepository queries, FeedPostAssembler assembler,
		CurrentMemberProvider currentMember) {
		this.posts = posts;
		this.queries = queries;
		this.assembler = assembler;
		this.currentMember = currentMember;
	}

	@Override
	public FeedPageResponse all(ListRequest request) {
		return list(request, null, Sort.LATEST, viewerId());
	}

	@Override
	public FeedPageResponse recommended(ListRequest request) {
		return list(request, null, Sort.RECOMMENDED, viewerId());
	}

	@Override
	public FeedPageResponse following(ListRequest request) {
		long viewerId = currentMember.requireActive().getId();
		return list(request, viewerId, Sort.LATEST, viewerId);
	}

	@Override
	@Transactional
	public FeedPostResponse detail(long id, boolean view) {
		Long viewerId = viewerId();
		Post post = posts.findLive(id).filter(found -> FeedRules.readable(found, viewerId)).orElseThrow(FeedRules::postNotFound);
		if (view && (viewerId == null || !post.isWrittenBy(viewerId))) {
			// 증가 쿼리가 영속성 컨텍스트를 비우므로 올린 값을 다시 읽는다.
			posts.increaseViewCount(id);
			post = posts.findLive(id).orElseThrow(FeedRules::postNotFound);
		}
		return assembler.toResponse(post, viewerId);
	}

	private FeedPageResponse list(ListRequest request, Long followerId, Sort defaultSort, Long viewerId) {
		FeedRules.page(request.page(), request.size());
		if (request.seed() < 0) throw FeedRules.badRequest("조회 조건을 확인해 주세요.");
		Condition condition = new Condition(FeedRules.categoryFilter(request.category()), FeedRules.searchTerms(request.q()),
			followerId, sort(request.sort(), defaultSort), request.seed());
		PostQueryRepository.Page page = queries.find(condition, request.page(), request.size());
		if (page.ids().isEmpty()) {
			return new FeedPageResponse(List.of(), request.page(), request.size(), false, page.totalCount());
		}
		Map<Long, Post> found = posts.findAllWithAuthors(page.ids()).stream()
			.collect(Collectors.toMap(Post::getId, Function.identity()));
		// 목록 SQL이 정한 순서를 지킨다. 그 사이 삭제된 글은 뺀다.
		List<Post> ordered = page.ids().stream().map(found::get).filter(post -> post != null && !post.isDeleted()).toList();
		return new FeedPageResponse(assembler.toResponses(ordered, viewerId), request.page(), request.size(), page.hasNext(),
			page.totalCount());
	}

	private Long viewerId() {
		return currentMember.find().map(Member::getId).orElse(null);
	}

	private static Sort sort(String value, Sort defaultSort) {
		if (value == null || value.isBlank()) return defaultSort;
		return switch (value) {
			case "recommended" -> Sort.RECOMMENDED;
			case "popular" -> Sort.POPULAR;
			case "views" -> Sort.VIEWS;
			case "latest" -> Sort.LATEST;
			default -> throw FeedRules.badRequest("조회 조건을 확인해 주세요.");
		};
	}
}
