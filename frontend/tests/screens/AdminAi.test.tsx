import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { AiFailuresView, AiUsageView } from '@/app/admin/ai/_components/ApiAdminAi';
import { aiMemberName } from '@/lib/administration/adminAi';

const noop = () => {};

describe('서버 AI 사용량·오류 화면 (14.10·14.11)', () => {
    it('월 요약과 회원별 사용량을 보여 준다', () => {
        const html = renderToStaticMarkup(<AiUsageView onRetry={noop} onPage={noop} load={{ status: 'ready', data: {
            summary: { month: '2026-11', totalUsed: 11, totalPending: 1, members: 2 }, totalElements: 2, page: 0, totalPages: 1,
            items: [
                { userId: 3, tag: null, nickname: '탈퇴회원', role: null, withdrawn: true, usedCount: 7, pendingCount: 0 },
                { userId: 2, tag: 'kim', nickname: '김개발', role: 'PREMIUM', withdrawn: false, usedCount: 4, pendingCount: 1 },
            ] } }} />);
        expect(html).toContain('사용 11회');
        expect(html).toContain('회원 2명');
        expect(html).toContain('<b>탈퇴회원 (탈퇴)</b>');
        expect(html).toContain('href="/users/kim"');
        expect(html).toContain('사용 4회 · 예약 1회');
    });
    it('사용 기록과 실패 기록이 없으면 안내한다', () => {
        const empty = { totalElements: 0, page: 0, totalPages: 0, items: [] };
        expect(renderToStaticMarkup(<AiUsageView onRetry={noop} onPage={noop}
            load={{ status: 'ready', data: { ...empty, summary: { month: '2026-10', totalUsed: 0, totalPending: 0, members: 0 } } }} />))
            .toContain('이 달의 추천 사용 기록이 없습니다.');
        expect(renderToStaticMarkup(<AiFailuresView onRetry={noop} onPage={noop} load={{ status: 'ready', data: empty }} />))
            .toContain('실패한 추천 요청이 없습니다.');
    });
    it('실패 기록의 시각·회원·요청을 보여 준다', () => {
        const html = renderToStaticMarkup(<AiFailuresView onRetry={noop} onPage={noop} load={{ status: 'ready', data: {
            totalElements: 1, page: 0, totalPages: 1, items: [{ requestId: 102, conversationId: 10, userId: 2, userTag: 'kim', nickname: '김개발',
                withdrawn: false, status: 'FAILED', createdAt: '2026-11-10T10:00:00', completedAt: '2026-11-10T10:00:05' }] } }} />);
        expect(html).toContain('2026-11-10 10:00');
        expect(html).toContain('FAILED');
        expect(html).toContain('김개발 · 요청 #102 · 대화 #10');
        expect(aiMemberName(null, false, 9)).toBe('회원 #9');
    });
});
