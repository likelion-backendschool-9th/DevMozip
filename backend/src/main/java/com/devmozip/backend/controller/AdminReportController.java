package com.devmozip.backend.controller;

import com.devmozip.backend.dto.admin.AdminPageResponse;
import com.devmozip.backend.dto.admin.AdminReportHandleRequest;
import com.devmozip.backend.dto.admin.AdminReportResponse;
import com.devmozip.backend.service.AdminPaging;
import com.devmozip.backend.service.AdminReportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * WBS 14.8 신고 내역 처리. 기존 /api/v1/admin/project-reports(공고·팀원 신고만)를 대신한다 (docs/admin-plan.md D5).
 * type·status는 문자열로 받아 Service가 400 안내와 함께 확인한다.
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminReportController {
	private final AdminReportService reports;

	public AdminReportController(AdminReportService reports) {
		this.reports = reports;
	}

	@GetMapping
	public AdminPageResponse<AdminReportResponse> list(@RequestParam(required = false) String type,
		@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "" + AdminPaging.DEFAULT_SIZE) int size) {
		return reports.list(type, status, page, size);
	}

	@PatchMapping("/{id}")
	public AdminReportResponse handle(@PathVariable long id, @Valid @RequestBody AdminReportHandleRequest request) {
		return reports.handle(id, request);
	}
}
