package com.devmozip.backend.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import com.devmozip.backend.dto.follow.FollowNotificationResponse;
import com.devmozip.backend.dto.follow.FollowResultResponse;
import com.devmozip.backend.entity.Follow;
import com.devmozip.backend.entity.FollowId;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.FollowRepository;
import com.devmozip.backend.security.member.CurrentMemberProvider;
import com.devmozip.backend.service.AccountVisibilityPolicy;
import com.devmozip.backend.service.FollowService;
import com.devmozip.backend.service.FollowTargetFinder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * 이미 팔로우한 상태의 팔로우와 팔로우하지 않은 상태의 언팔로우는 아무것도 바꾸지 않고 현재 상태를 응답한다.
 * 비공개 계정(WBS 17.7)은 팔로우·언팔로우할 수 없다(403). 응답의 팔로워 수가 드러나지 않게 하기 위해서다.
 * 알림 설정은 요청자 자신의 팔로우 행만 바꾸고 대상 정보를 돌려주지 않으므로 공개 여부를 보지 않는다.
 */
@Service
public class FollowServiceImpl implements FollowService {
	// ERD DATETIME은 한국 시각의 timestamp without time zone으로 저장한다(좋아요 목록과 같은 기준).
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final FollowRepository follows;
	private final FollowTargetFinder targets;
	private final CurrentMemberProvider currentMember;
	private final TransactionTemplate transactions;
	private final AccountVisibilityPolicy visibility;

	public FollowServiceImpl(FollowRepository follows, FollowTargetFinder targets, CurrentMemberProvider currentMember,
		PlatformTransactionManager transactionManager, AccountVisibilityPolicy visibility) {
		this.follows = follows;
		this.targets = targets;
		this.currentMember = currentMember;
		this.transactions = new TransactionTemplate(transactionManager);
		this.visibility = visibility;
	}

	@Override
	public FollowResultResponse follow(String slug) {
		try {
			return transactions.execute(status -> {
				Relation relation = visibleRelation(slug);
				if (!follows.existsById(relation.id())) {
					follows.saveAndFlush(Follow.create(relation.requester(), relation.target(), LocalDateTime.now(SEOUL)));
				}
				return result(relation);
			});
		} catch (DataIntegrityViolationException duplicate) {
			// 같은 팔로우 요청이 동시에 와서 다른 요청이 먼저 저장했다. 실패한 트랜잭션은 이미 끝났으므로 새로 읽어 현재 상태를 응답한다.
			return transactions.execute(status -> result(visibleRelation(slug)));
		}
	}

	@Override
	@Transactional
	public FollowResultResponse unfollow(String slug) {
		Relation relation = visibleRelation(slug);
		// 알림 설정은 같은 행에 있으므로 함께 지워진다.
		follows.deleteRelation(relation.requester().getId(), relation.target().getId());
		return result(relation);
	}

	@Override
	@Transactional
	public FollowNotificationResponse updateNotification(String slug, boolean activityNotificationEnabled) {
		Relation relation = relation(slug);
		int changed = follows.updateActivityNotification(relation.requester().getId(), relation.target().getId(), activityNotificationEnabled);
		if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "팔로우한 개발자의 알림만 설정할 수 있습니다.");
		return new FollowNotificationResponse(activityNotificationEnabled);
	}

	private Relation relation(String slug) {
		Member requester = currentMember.requireActive();
		Member target = targets.find(slug);
		if (requester.getId().equals(target.getId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신은 팔로우할 수 없습니다.");
		}
		return new Relation(requester, target);
	}

	// 자기 자신은 relation()에서 먼저 거절되므로 여기서는 다른 회원의 비공개 계정만 막힌다.
	private Relation visibleRelation(String slug) {
		Relation relation = relation(slug);
		visibility.requireVisibleAccount(relation.requester().getId(), relation.target());
		return relation;
	}

	private FollowResultResponse result(Relation relation) {
		var follow = follows.findById(relation.id());
		return new FollowResultResponse(follow.isPresent(), follow.map(Follow::isActivityNotificationEnabled).orElse(false),
			follows.countFollowers(relation.target().getId()));
	}

	private record Relation(Member requester, Member target) {
		FollowId id() {
			return new FollowId(requester.getId(), target.getId());
		}
	}
}
