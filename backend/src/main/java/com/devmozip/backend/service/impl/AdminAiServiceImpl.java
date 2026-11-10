package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.YearMonth;
import java.util.regex.Pattern;
import com.devmozip.backend.dto.admin.AdminAiFailureResponse;
import com.devmozip.backend.dto.admin.AdminAiUsageResponse;
import com.devmozip.backend.dto.admin.AdminAiUsageSummary;
import com.devmozip.backend.dto.admin.AdminPageResponse;
import com.devmozip.backend.dto.admin.AdminSummaryPageResponse;
import com.devmozip.backend.repository.AdminAiQueryRepository;
import com.devmozip.backend.security.admin.AdminAccess;
import com.devmozip.backend.service.AdminAiService;
import com.devmozip.backend.service.AdminPaging;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 관리자 AI 사용량·오류 조회 (docs/admin-plan.md 5.9). 사용 월은 AI 사용량과 같은 YearMonth 문자열(YYYY-MM)이다. */
@Service
public class AdminAiServiceImpl implements AdminAiService {
	private static final Pattern MONTH = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

	private final AdminAccess access;
	private final AdminAiQueryRepository query;
	private final Clock clock;

	public AdminAiServiceImpl(AdminAccess access, AdminAiQueryRepository query, @Qualifier("adminClock") Clock clock) {
		this.access = access;
		this.query = query;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public AdminSummaryPageResponse<AdminAiUsageSummary, AdminAiUsageResponse> usage(String month, int page, int size) {
		access.require();
		AdminPaging.request(page, size);
		String target = month == null || month.isBlank() ? YearMonth.now(clock).toString() : month;
		if (!MONTH.matcher(target).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "월을 YYYY-MM 형식으로 입력해 주세요.");
		AdminAiUsageSummary summary = query.usageSummary(target);
		return AdminSummaryPageResponse.of(summary, AdminPageResponse.of(query.usage(target, page, size), summary.members(), page, size));
	}

	@Override
	@Transactional(readOnly = true)
	public AdminPageResponse<AdminAiFailureResponse> failures(int page, int size) {
		access.require();
		AdminPaging.request(page, size);
		return AdminPageResponse.of(query.failures(page, size), query.failureCount(), page, size);
	}
}
