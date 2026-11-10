package com.devmozip.backend.controller;

import com.devmozip.backend.dto.follow.FollowNotificationRequest;
import com.devmozip.backend.dto.follow.FollowNotificationResponse;
import com.devmozip.backend.dto.follow.FollowPageResponse;
import com.devmozip.backend.dto.follow.FollowResultResponse;
import com.devmozip.backend.dto.follow.FollowStatusResponse;
import com.devmozip.backend.service.FollowQueryService;
import com.devmozip.backend.service.FollowService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * WBS 9.1~9.5 팔로우 API와 팔로우 상태 API. {slug}는 users.tag 원문이다.
 * 요청자는 Service가 인증 정보에서 찾는다. 조회는 공개이고 변경은 로그인이 필요하다(SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/users/{slug}")
public class FollowController {
	private final FollowService follows;
	private final FollowQueryService queries;

	public FollowController(FollowService follows, FollowQueryService queries) {
		this.follows = follows;
		this.queries = queries;
	}

	@GetMapping("/follow")
	public FollowStatusResponse status(@PathVariable String slug) {
		return queries.status(slug);
	}

	@PostMapping("/follow")
	public FollowResultResponse follow(@PathVariable String slug) {
		return follows.follow(slug);
	}

	@DeleteMapping("/follow")
	public FollowResultResponse unfollow(@PathVariable String slug) {
		return follows.unfollow(slug);
	}

	@GetMapping("/followers")
	public FollowPageResponse followers(@PathVariable String slug,
		@RequestParam(required = false) String q,
		@RequestParam(defaultValue = "false") boolean available,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size) {
		return queries.followers(slug, q, available, page, size);
	}

	@GetMapping("/following")
	public FollowPageResponse following(@PathVariable String slug,
		@RequestParam(required = false) String q,
		@RequestParam(defaultValue = "false") boolean available,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size) {
		return queries.following(slug, q, available, page, size);
	}

	@PutMapping("/notify")
	public FollowNotificationResponse updateNotification(@PathVariable String slug, @Valid @RequestBody FollowNotificationRequest request) {
		return follows.updateNotification(slug, request.activityNotificationEnabled());
	}
}
