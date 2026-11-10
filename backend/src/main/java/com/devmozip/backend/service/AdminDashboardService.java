package com.devmozip.backend.service;

import com.devmozip.backend.dto.admin.AdminDashboardResponse;

/** WBS 14.1 관리자 대시보드 (docs/admin-plan.md 5.2). */
public interface AdminDashboardService {
	AdminDashboardResponse dashboard();
}
