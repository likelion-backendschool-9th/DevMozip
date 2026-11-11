import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { DashboardView } from '@/app/admin/_components/ApiAdminDashboard';

const noop = () => {};
const dashboard = { members: 4, premiumMembers: 1, suspendedMembers: 1, projects: 3, posts: 9, pendingReports: 2, aiRecommendationsThisMonth: 5, recentActions: [] };

describe('서버 관리자 대시보드 (14.1)', () => {
    it('수치 카드를 보여 준다', () => {
        const html = renderToStaticMarkup(<DashboardView onRetry={noop} load={{ status: 'ready', data: dashboard }} />);
        for (const text of ['<span>회원</span><strong>4</strong>', '<span>프리미엄</span><strong>1</strong>', '<span>미처리 신고</span><strong>2</strong>',
            '<span>이번 달 AI 추천</span><strong>5</strong>', '아직 변경 이력이 없습니다.']) expect(html).toContain(text);
    });
    it('최근 운영 변경을 시각·처리자·내용으로 보여 준다', () => {
        const html = renderToStaticMarkup(<DashboardView onRetry={noop} load={{ status: 'ready', data: { ...dashboard, recentActions: [
            { type: 'REPORT', actorNickname: '운영자', summary: '신고 처리 완료 · 사용자 신고 #1', createdAt: '2026-11-10T10:05:00' },
        ] } }} />);
        expect(html).toContain('2026-11-10 10:05 · 운영자 · 신고 처리 완료 · 사용자 신고 #1');
    });
    it('불러오지 못하면 다시 시도를 보여 준다', () => {
        expect(renderToStaticMarkup(<DashboardView onRetry={noop} load={{ status: 'error', message: '실패' }} />)).toContain('다시 시도');
    });
});
