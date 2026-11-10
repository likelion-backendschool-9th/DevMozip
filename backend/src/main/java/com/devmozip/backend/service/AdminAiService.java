package com.devmozip.backend.service;

import com.devmozip.backend.dto.admin.AdminAiFailureResponse;
import com.devmozip.backend.dto.admin.AdminAiUsageResponse;
import com.devmozip.backend.dto.admin.AdminAiUsageSummary;
import com.devmozip.backend.dto.admin.AdminPageResponse;
import com.devmozip.backend.dto.admin.AdminSummaryPageResponse;

/** WBS 14.10 AI 사용량 확인, 14.11 AI 오류 관리 (docs/admin-plan.md 5.9). 조회만 한다. */
public interface AdminAiService {
	/** month는 YYYY-MM이며 비어 있으면 이번 달(한국 시각)이다. */
	AdminSummaryPageResponse<AdminAiUsageSummary, AdminAiUsageResponse> usage(String month, int page, int size);

	AdminPageResponse<AdminAiFailureResponse> failures(int page, int size);
}
