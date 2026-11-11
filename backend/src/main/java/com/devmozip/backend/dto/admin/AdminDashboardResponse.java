package com.devmozip.backend.dto.admin;

import java.util.List;

/**
 * 관리자 대시보드 (WBS 14.1, docs/admin-plan.md 5.2). 수치 기준:
 * members는 탈퇴하지 않은 회원, premiumMembers는 이용 기간 안의 구독이 있는 회원, suspendedMembers는 정지 회원,
 * projects는 삭제되지 않은 공고, posts는 삭제되지 않은 공개(VISIBLE) 게시글, pendingReports는 접수·검토 중인 신고,
 * aiRecommendationsThisMonth는 이번 달(한국 시각) AI 추천 사용 횟수의 합이다. recentActions는 최근 제재·신고 처리 12건이다.
 */
public record AdminDashboardResponse(long members, long premiumMembers, long suspendedMembers, long projects, long posts, long pendingReports,
	long aiRecommendationsThisMonth, List<AdminRecentActionResponse> recentActions) {
}
