package com.devmozip.backend.service.impl;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.devmozip.backend.dto.admin.AdminPageResponse;
import com.devmozip.backend.dto.admin.AdminPaymentResponse;
import com.devmozip.backend.dto.admin.AdminPaymentSummary;
import com.devmozip.backend.dto.admin.AdminPaymentTotal;
import com.devmozip.backend.dto.admin.AdminSubscriptionResponse;
import com.devmozip.backend.dto.admin.AdminSubscriptionSummary;
import com.devmozip.backend.dto.admin.AdminSummaryPageResponse;
import com.devmozip.backend.entity.enums.PaymentStatus;
import com.devmozip.backend.entity.enums.SubscriptionStatus;
import com.devmozip.backend.repository.AdminBillingQueryRepository;
import com.devmozip.backend.security.admin.AdminAccess;
import com.devmozip.backend.service.AdminBillingService;
import com.devmozip.backend.service.AdminPaging;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 구독·결제 조회 (docs/admin-plan.md 5.10). 결제 클라이언트(포트원)를 주입받지 않는다. */
@Service
public class AdminBillingServiceImpl implements AdminBillingService {
	private final AdminAccess access;
	private final AdminBillingQueryRepository query;

	public AdminBillingServiceImpl(AdminAccess access, AdminBillingQueryRepository query) {
		this.access = access;
		this.query = query;
	}

	@Override
	@Transactional(readOnly = true)
	public AdminSummaryPageResponse<AdminSubscriptionSummary, AdminSubscriptionResponse> subscriptions(String status, int page, int size) {
		access.require();
		SubscriptionStatus filter = AdminPaging.option(SubscriptionStatus.class, status, "구독 상태를 확인해 주세요.");
		var rows = query.searchSubscriptions(filter, AdminPaging.request(page, size));
		Map<String, Long> counts = new LinkedHashMap<>();
		for (SubscriptionStatus value : SubscriptionStatus.values()) counts.put(value.name(), 0L);
		for (Object[] row : query.countSubscriptionsByStatus()) counts.put(((SubscriptionStatus) row[0]).name(), (Long) row[1]);
		long current = SubscriptionStatus.CURRENT.stream().mapToLong(value -> counts.get(value.name())).sum();
		return AdminSummaryPageResponse.of(new AdminSubscriptionSummary(counts, current), AdminPageResponse.of(rows, AdminSubscriptionResponse::of));
	}

	@Override
	@Transactional(readOnly = true)
	public AdminSummaryPageResponse<AdminPaymentSummary, AdminPaymentResponse> payments(String status, int page, int size) {
		access.require();
		PaymentStatus filter = AdminPaging.option(PaymentStatus.class, status, "결제 상태를 확인해 주세요.");
		var rows = query.searchPayments(filter, AdminPaging.request(page, size));
		List<AdminPaymentTotal> totals = query.paidTotals().stream()
			.map(row -> new AdminPaymentTotal((String) row[0], (BigDecimal) row[1])).toList();
		return AdminSummaryPageResponse.of(new AdminPaymentSummary(totals), AdminPageResponse.of(rows, AdminPaymentResponse::of));
	}
}
