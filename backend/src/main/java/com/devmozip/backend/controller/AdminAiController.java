package com.devmozip.backend.controller;

import com.devmozip.backend.dto.admin.AdminAiFailureResponse;
import com.devmozip.backend.dto.admin.AdminAiUsageResponse;
import com.devmozip.backend.dto.admin.AdminAiUsageSummary;
import com.devmozip.backend.dto.admin.AdminPageResponse;
import com.devmozip.backend.dto.admin.AdminSummaryPageResponse;
import com.devmozip.backend.service.AdminAiService;
import com.devmozip.backend.service.AdminPaging;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** WBS 14.10 AI 호출 사용량 확인, 14.11 AI 호출 오류 관리. 조회만 한다. */
@RestController
@RequestMapping("/api/v1/admin/ai")
public class AdminAiController {
	private final AdminAiService ai;

	public AdminAiController(AdminAiService ai) {
		this.ai = ai;
	}

	@GetMapping("/usage")
	public AdminSummaryPageResponse<AdminAiUsageSummary, AdminAiUsageResponse> usage(@RequestParam(required = false) String month,
		@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "" + AdminPaging.DEFAULT_SIZE) int size) {
		return ai.usage(month, page, size);
	}

	@GetMapping("/logs")
	public AdminPageResponse<AdminAiFailureResponse> logs(@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "" + AdminPaging.DEFAULT_SIZE) int size) {
		return ai.failures(page, size);
	}
}
