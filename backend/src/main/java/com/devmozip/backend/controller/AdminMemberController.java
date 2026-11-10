package com.devmozip.backend.controller;

import com.devmozip.backend.dto.admin.AdminBanRequest;
import com.devmozip.backend.dto.admin.AdminMemberResponse;
import com.devmozip.backend.dto.admin.AdminPageResponse;
import com.devmozip.backend.service.AdminMemberService;
import com.devmozip.backend.service.AdminPaging;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * WBS 14.2 회원 조회, 14.3 회원 제재. /api/v1/admin/**는 SecurityConfig가 ADMIN 역할을 요구하고,
 * Service가 DB의 역할·상태를 다시 확인한다. status·role은 문자열로 받아 Service가 400 안내와 함께 확인한다.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminMemberController {
	private final AdminMemberService members;

	public AdminMemberController(AdminMemberService members) {
		this.members = members;
	}

	@GetMapping
	public AdminPageResponse<AdminMemberResponse> list(@RequestParam(required = false) String q,
		@RequestParam(required = false) String status, @RequestParam(required = false) String role,
		@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "" + AdminPaging.DEFAULT_SIZE) int size) {
		return members.list(q, status, role, page, size);
	}

	@PatchMapping("/{slug}/ban")
	public AdminMemberResponse ban(@PathVariable String slug, @Valid @RequestBody AdminBanRequest request) {
		return members.ban(slug, request);
	}
}
