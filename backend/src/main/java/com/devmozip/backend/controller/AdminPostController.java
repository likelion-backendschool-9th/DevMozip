package com.devmozip.backend.controller;

import com.devmozip.backend.dto.admin.AdminActionRequest;
import com.devmozip.backend.dto.admin.AdminPageResponse;
import com.devmozip.backend.dto.admin.AdminPostResponse;
import com.devmozip.backend.dto.admin.AdminRestrictRequest;
import com.devmozip.backend.service.AdminPaging;
import com.devmozip.backend.service.AdminPostService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * WBS 14.6 게시글·댓글 관리, 14.7 표시 여부. target=POST(기본)·COMMENT로 대상을 나눈다 (docs/admin-plan.md D4).
 * PUT은 관리자 삭제(D3), PATCH visibility는 게시글의 관리자 숨김·복원이다.
 */
@RestController
@RequestMapping("/api/v1/admin/posts")
public class AdminPostController {
	private final AdminPostService posts;

	public AdminPostController(AdminPostService posts) {
		this.posts = posts;
	}

	@GetMapping
	public AdminPageResponse<?> list(@RequestParam(required = false) String target, @RequestParam(required = false) String q,
		@RequestParam(required = false) String visibility, @RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "" + AdminPaging.DEFAULT_SIZE) int size) {
		return posts.list(target, q, visibility, page, size);
	}

	@PutMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void act(@PathVariable long id, @RequestParam(required = false) String target,
		@Valid @RequestBody AdminActionRequest request) {
		posts.act(id, target, request);
	}

	@PatchMapping("/{id}/visibility")
	public AdminPostResponse restrict(@PathVariable long id, @RequestParam(required = false) String target,
		@Valid @RequestBody AdminRestrictRequest request) {
		return posts.restrict(id, target, request);
	}
}
