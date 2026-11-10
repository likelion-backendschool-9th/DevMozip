// 14.1 관리자 대시보드의 응답 해석 (docs/admin-plan.md 5.2, D7).
import { fields } from './adminApi';
import type { AdminDashboard } from '@/types/adminApi';

export function parseAdminDashboard(value: unknown): AdminDashboard {
    const f = fields(value, 'dashboard');
    return {
        members: f.num('members'), premiumMembers: f.num('premiumMembers'), suspendedMembers: f.num('suspendedMembers'),
        projects: f.num('projects'), posts: f.num('posts'), pendingReports: f.num('pendingReports'),
        aiRecommendationsThisMonth: f.num('aiRecommendationsThisMonth'),
        recentActions: f.list('recentActions').map(raw => {
            const a = fields(raw, 'action');
            return { type: a.oneOf('type', ['SANCTION', 'REPORT'] as const), actorNickname: a.optStr('actorNickname'), summary: a.str('summary'), createdAt: a.str('createdAt') };
        }),
    };
}

/** 화면의 수치 카드. Mock 대시보드와 같은 항목에 이번 달 AI 추천 수를 더했다(기획서 6.12의 AI 사용량). */
export const dashboardStats = (d: AdminDashboard): [string, number][] => [
    ['회원', d.members], ['프리미엄', d.premiumMembers], ['프로젝트', d.projects], ['게시글', d.posts],
    ['미처리 신고', d.pendingReports], ['정지 회원', d.suspendedMembers], ['이번 달 AI 추천', d.aiRecommendationsThisMonth],
];
