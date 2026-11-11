package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.devmozip.backend.dto.admin.AdminDashboardResponse;
import com.devmozip.backend.dto.admin.AdminRecentActionResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Report;
import com.devmozip.backend.entity.Sanction;
import com.devmozip.backend.entity.enums.PostVisibility;
import com.devmozip.backend.entity.enums.SubscriptionStatus;
import com.devmozip.backend.repository.AdminAiQueryRepository;
import com.devmozip.backend.repository.AdminDashboardQueryRepository;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.admin.AdminAccess;
import com.devmozip.backend.service.AdminDashboardService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 대시보드 집계 (docs/admin-plan.md 5.2, D7). 최근 운영 변경은 제재 기록과 신고 처리 기록을 시간순으로 합친다. */
@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {
	static final int RECENT_LIMIT = 12;
	private static final Map<Sanction.Type, String> SANCTION_LABELS = Map.of(Sanction.Type.WARNED, "회원 경고", Sanction.Type.SUSPENDED, "회원 정지");
	private static final Map<String, String> REPORT_TYPE_LABELS = Map.of(
		"PROJECT", "공고", "MEMBER", "프로젝트 팀원", "USER", "사용자", "POST", "게시글", "COMMENT", "댓글");
	private static final Map<Report.Status, String> REPORT_STATUS_LABELS = Map.of(
		Report.Status.REVIEWING, "검토 중", Report.Status.RESOLVED, "처리 완료", Report.Status.REJECTED, "기각", Report.Status.AWATING, "접수");

	private final AdminAccess access;
	private final AdminDashboardQueryRepository query;
	private final AdminAiQueryRepository ai;
	private final MemberRepository members;
	private final Clock clock;

	public AdminDashboardServiceImpl(AdminAccess access, AdminDashboardQueryRepository query, AdminAiQueryRepository ai, MemberRepository members,
		@Qualifier("adminClock") Clock clock) {
		this.access = access;
		this.query = query;
		this.ai = ai;
		this.members = members;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public AdminDashboardResponse dashboard() {
		access.require();
		return new AdminDashboardResponse(
			query.countMembers(),
			query.countSubscribers(SubscriptionStatus.CURRENT),
			query.countMembersByStatus(Member.MemberStatus.SUSPENDED),
			query.countLiveProjects(),
			query.countLivePosts(PostVisibility.VISIBLE),
			query.countReports(EnumSet.of(Report.Status.AWATING, Report.Status.REVIEWING)),
			ai.usageSummary(YearMonth.now(clock).toString()).totalUsed(),
			recentActions());
	}

	private List<AdminRecentActionResponse> recentActions() {
		PageRequest recent = PageRequest.of(0, RECENT_LIMIT);
		List<AdminRecentActionResponse> actions = new ArrayList<>();
		for (Sanction sanction : query.recentSanctions(recent)) {
			actions.add(new AdminRecentActionResponse("SANCTION", sanction.getAdmin().getNickname(),
				SANCTION_LABELS.get(sanction.getType()) + " · " + sanction.getUser().getNickname(), sanction.getStartsAt()));
		}
		// 철회한 관리자는 ERD에 칼럼이 없어 남지 않는다. 경고에서 정지로 바뀌면 경고 철회와 정지가 함께 나온다.
		for (Sanction sanction : query.recentRevocations(recent)) {
			actions.add(new AdminRecentActionResponse("SANCTION", null,
				SANCTION_LABELS.get(sanction.getType()) + " 철회 · " + sanction.getUser().getNickname(), sanction.getRevokedAt()));
		}
		List<Report> reports = query.recentHandledReports(recent);
		Map<Long, Member> handlers = members.findAllById(reports.stream().map(Report::getHandledBy).filter(Objects::nonNull).distinct().toList())
			.stream().collect(Collectors.toMap(Member::getId, Function.identity()));
		for (Report report : reports) {
			Member handler = report.getHandledBy() == null ? null : handlers.get(report.getHandledBy());
			actions.add(new AdminRecentActionResponse("REPORT", handler == null ? null : handler.getNickname(),
				"신고 " + REPORT_STATUS_LABELS.get(report.getStatus()) + " · " + REPORT_TYPE_LABELS.get(AdminReportServiceImpl.typeOf(report)) + " 신고 #" + report.getId(),
				report.getHandledAt()));
		}
		return actions.stream().sorted(Comparator.comparing(AdminRecentActionResponse::createdAt).reversed()).limit(RECENT_LIMIT).toList();
	}
}
