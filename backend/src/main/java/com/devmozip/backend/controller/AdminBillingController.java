package com.devmozip.backend.controller;

import com.devmozip.backend.dto.admin.AdminPaymentResponse;
import com.devmozip.backend.dto.admin.AdminPaymentSummary;
import com.devmozip.backend.dto.admin.AdminSubscriptionResponse;
import com.devmozip.backend.dto.admin.AdminSubscriptionSummary;
import com.devmozip.backend.dto.admin.AdminSummaryPageResponse;
import com.devmozip.backend.service.AdminBillingService;
import com.devmozip.backend.service.AdminPaging;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** WBS 14.12 구독 관리(GET /api/v1/admin/subscriptions), 14.13 결제 관리(GET /api/v1/admin/payments). 조회만 한다. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminBillingController {
	private final AdminBillingService billing;

	public AdminBillingController(AdminBillingService billing) {
		this.billing = billing;
	}

	@GetMapping("/subscriptions")
	public AdminSummaryPageResponse<AdminSubscriptionSummary, AdminSubscriptionResponse> subscriptions(
		@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "" + AdminPaging.DEFAULT_SIZE) int size) {
		return billing.subscriptions(status, page, size);
	}

	@GetMapping("/payments")
	public AdminSummaryPageResponse<AdminPaymentSummary, AdminPaymentResponse> payments(@RequestParam(required = false) String status,
		@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "" + AdminPaging.DEFAULT_SIZE) int size) {
		return billing.payments(status, page, size);
	}
}
