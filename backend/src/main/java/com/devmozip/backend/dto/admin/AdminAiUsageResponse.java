package com.devmozip.backend.dto.admin;

/** WBS 14.10 회원별 AI 사용량. 탈퇴 회원(#135)의 사용 기록도 남아 있어 withdrawn으로 표시한다. */
public record AdminAiUsageResponse(long userId, String tag, String nickname, String role, boolean withdrawn, int usedCount, int pendingCount) {
}
