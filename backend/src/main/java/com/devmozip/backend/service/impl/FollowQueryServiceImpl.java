package com.devmozip.backend.service.impl;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.devmozip.backend.dto.follow.FollowPageResponse;
import com.devmozip.backend.dto.follow.FollowStatusResponse;
import com.devmozip.backend.dto.follow.FollowUserResponse;
import com.devmozip.backend.entity.Follow;
import com.devmozip.backend.entity.FollowId;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.FollowQueryRepository;
import com.devmozip.backend.repository.FollowQueryRepository.Direction;
import com.devmozip.backend.repository.FollowRepository;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.member.CurrentMemberProvider;
import com.devmozip.backend.service.AccountVisibilityPolicy;
import com.devmozip.backend.service.FollowQueryService;
import com.devmozip.backend.service.FollowTargetFinder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 수와 목록이 한 시점의 값이 되도록 REPEATABLE_READ로 읽는다. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class FollowQueryServiceImpl implements FollowQueryService {
	private static final int MAX_QUERY_LENGTH = 100;
	private static final int MAX_SIZE = 100;

	private final FollowRepository follows;
	private final FollowQueryRepository queries;
	private final MemberRepository members;
	private final FollowTargetFinder targets;
	private final CurrentMemberProvider currentMember;
	private final AccountVisibilityPolicy visibility;

	public FollowQueryServiceImpl(FollowRepository follows, FollowQueryRepository queries, MemberRepository members,
		FollowTargetFinder targets, CurrentMemberProvider currentMember, AccountVisibilityPolicy visibility) {
		this.follows = follows;
		this.queries = queries;
		this.members = members;
		this.targets = targets;
		this.currentMember = currentMember;
		this.visibility = visibility;
	}

	@Override
	public FollowStatusResponse status(String slug) {
		Member target = targets.find(slug);
		Optional<Member> requester = currentMember.find();
		// 비공개 계정의 팔로우 상태는 본인에게만 보인다(403 "비공개 계정입니다.").
		visibility.requireVisibleAccount(requester.map(Member::getId).orElse(null), target);
		// 본인 프로필이면 자기 자신과의 관계가 없으므로 false가 된다. 수는 비공개 계정을 포함해 모든 관계를 센다.
		Optional<Follow> mine = requester
			.flatMap(member -> follows.findById(new FollowId(member.getId(), target.getId())));
		return new FollowStatusResponse(follows.countFollowers(target.getId()), follows.countFollowing(target.getId()),
			mine.isPresent(), mine.map(Follow::isActivityNotificationEnabled).orElse(false));
	}

	@Override
	public FollowPageResponse followers(String slug, String query, boolean availableOnly, int page, int size) {
		return list(slug, Direction.FOLLOWERS, query, availableOnly, page, size);
	}

	@Override
	public FollowPageResponse following(String slug, String query, boolean availableOnly, int page, int size) {
		return list(slug, Direction.FOLLOWING, query, availableOnly, page, size);
	}

	private FollowPageResponse list(String slug, Direction direction, String query, boolean availableOnly, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_SIZE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지와 조회 개수를 확인해 주세요.");
		}
		if (query != null && query.length() > MAX_QUERY_LENGTH) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어는 100자 이내로 입력해 주세요.");
		}
		String keyword = query == null || query.isBlank() ? null : query.trim();
		Member target = targets.find(slug);
		Long requesterId = currentMember.find().map(Member::getId).orElse(null);
		// 비공개 계정의 목록은 본인에게만 보인다. 목록 항목의 비공개 계정은 Repository 조건에서 뺀다.
		visibility.requireVisibleAccount(requesterId, target);

		var result = queries.find(target.getId(), direction, requesterId, keyword, availableOnly, page, size);
		var byId = members.findAllById(result.rows().stream().map(FollowQueryRepository.Row::userId).toList()).stream()
			.collect(Collectors.toMap(Member::getId, Function.identity()));
		var items = result.rows().stream()
			.map(row -> {
				Member member = byId.get(row.userId());
				return member == null ? null : FollowUserResponse.from(member, row.followed(), row.activityNotificationEnabled());
			})
			.filter(Objects::nonNull)
			.toList();
		boolean hasNext = (long) (page + 1) * size < result.total();
		return new FollowPageResponse(items, page, size, hasNext, result.total());
	}
}
