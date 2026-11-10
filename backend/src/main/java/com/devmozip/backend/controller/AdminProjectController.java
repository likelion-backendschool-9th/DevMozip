package com.devmozip.backend.controller;

import com.devmozip.backend.dto.admin.AdminActionRequest;
import com.devmozip.backend.dto.admin.AdminPageResponse;
import com.devmozip.backend.dto.admin.AdminProjectResponse;
import com.devmozip.backend.dto.admin.AdminRestrictRequest;
import com.devmozip.backend.service.AdminPaging;
import com.devmozip.backend.service.AdminProjectService;
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

/** WBS 14.4 프로젝트 공고 관리, 14.5 표시 여부. PUT은 관리자 삭제(D3), PATCH visibility는 관리자 숨김·복원(D2)이다. */
@RestController
@RequestMapping("/api/v1/admin/projects")
public class AdminProjectController {
	private final AdminProjectService projects;

	public AdminProjectController(AdminProjectService projects) {
		this.projects = projects;
	}

	@GetMapping
	public AdminPageResponse<AdminProjectResponse> list(@RequestParam(required = false) String q,
		@RequestParam(required = false) String status, @RequestParam(required = false) String restricted,
		@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "" + AdminPaging.DEFAULT_SIZE) int size) {
		return projects.list(q, status, restricted, page, size);
	}

	@PutMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void act(@PathVariable long id, @Valid @RequestBody AdminActionRequest request) {
		projects.act(id, request);
	}

	@PatchMapping("/{id}/visibility")
	public AdminProjectResponse restrict(@PathVariable long id, @Valid @RequestBody AdminRestrictRequest request) {
		return projects.restrict(id, request);
	}
}
