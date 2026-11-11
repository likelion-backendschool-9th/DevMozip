package com.devmozip.backend.controller;

import com.devmozip.backend.dto.admin.AdminDashboardResponse;
import com.devmozip.backend.service.AdminDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** WBS 14.1 관리자 대시보드. */
@RestController
public class AdminDashboardController {
	private final AdminDashboardService dashboard;

	public AdminDashboardController(AdminDashboardService dashboard) {
		this.dashboard = dashboard;
	}

	@GetMapping("/api/v1/admin/dashboard")
	public AdminDashboardResponse dashboard() {
		return dashboard.dashboard();
	}
}
