// 14.10 AI 사용량·14.11 AI 오류의 응답 해석 (docs/admin-plan.md 5.9, D6). 추천 조건은 받지도 넘기지도 않는다.
import { fields, parseAdminPage, type QueryRule } from './adminApi';
import type { AdminAiFailure, AdminAiUsage, AdminAiUsageSummary, AdminSummaryPage } from '@/types/adminApi';

export const aiUsageQueryRules: Record<string, QueryRule> = { month: 'month' };

export function parseAiUsagePage(value: unknown): AdminSummaryPage<AdminAiUsageSummary, AdminAiUsage> {
    const s = fields(fields(value, 'usage').obj('summary'), 'summary');
    const page = parseAdminPage(value, raw => {
        const f = fields(raw, 'usageItem');
        return {
            userId: f.num('userId'), tag: f.optStr('tag'), nickname: f.optStr('nickname'), role: f.optStr('role'), withdrawn: f.bool('withdrawn'),
            usedCount: f.num('usedCount'), pendingCount: f.num('pendingCount'),
        };
    });
    return { summary: { month: s.str('month'), totalUsed: s.num('totalUsed'), totalPending: s.num('totalPending'), members: s.num('members') }, ...page };
}

export function parseAiFailure(value: unknown): AdminAiFailure {
    const f = fields(value, 'failure');
    return {
        requestId: f.num('requestId'), conversationId: f.num('conversationId'), userId: f.optNum('userId'), userTag: f.optStr('userTag'),
        nickname: f.optStr('nickname'), withdrawn: f.bool('withdrawn'), status: f.str('status'), createdAt: f.str('createdAt'),
        completedAt: f.optStr('completedAt'),
    };
}

/** 회원 이름 표시. 탈퇴 회원은 표시를 붙이고, 회원 행이 없으면 ID를 쓴다. */
export const aiMemberName = (nickname: string | null, withdrawn: boolean, userId: number | null) =>
    nickname ? (withdrawn ? `${nickname} (탈퇴)` : nickname) : userId ? `회원 #${userId}` : '-';
