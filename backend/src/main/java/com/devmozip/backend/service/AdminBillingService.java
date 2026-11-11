package com.devmozip.backend.service;

import com.devmozip.backend.dto.admin.AdminPaymentResponse;
import com.devmozip.backend.dto.admin.AdminPaymentSummary;
import com.devmozip.backend.dto.admin.AdminSubscriptionResponse;
import com.devmozip.backend.dto.admin.AdminSubscriptionSummary;
import com.devmozip.backend.dto.admin.AdminSummaryPageResponse;

/** WBS 14.12 구독 관리, 14.13 결제 관리 (docs/admin-plan.md 5.10). 조회만 하며 환불·취소·포트원 호출은 없다. */
public interface AdminBillingService {
	/** status는 SubscriptionStatus 이름이다. 최신 구독 ID순이다. */
	AdminSummaryPageResponse<AdminSubscriptionSummary, AdminSubscriptionResponse> subscriptions(String status, int page, int size);

	/** status는 PaymentStatus 이름이다. 최신 결제 ID순이다. */
	AdminSummaryPageResponse<AdminPaymentSummary, AdminPaymentResponse> payments(String status, int page, int size);
}
