import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
vi.mock('@/lib/auth/session', () => ({ getSession: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { getSession } from '@/lib/auth/session';
import { GET as usage } from './route';
import { GET as logs } from '../logs/route';

const usageBody = { summary: { month: '2026-11', totalUsed: 11, totalPending: 1, members: 2 },
    items: [{ userId: 3, tag: null, nickname: '탈퇴회원', role: null, withdrawn: true, usedCount: 7, pendingCount: 0 }], totalElements: 2, page: 0, totalPages: 1 };
const failure = { requestId: 102, conversationId: 10, userId: 2, userTag: 'kim', nickname: '김개발', withdrawn: false, status: 'FAILED',
    createdAt: '2026-11-10T10:00:00', completedAt: '2026-11-10T10:00:05' };
beforeEach(() => {
    vi.mocked(backendFetch).mockReset();
    vi.mocked(getSession).mockResolvedValue({ username: 'admin@devmozip.demo', role: 'ADMIN', isAdmin: true, token: 't' } as never);
});

describe('AI 사용량·오류 BFF (14.10·14.11)', () => {
    it('월 조건만 보내고 요약과 목록 형식을 확인한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(usageBody));
        expect(await (await usage(new Request('http://localhost/api/admin/ai/usage?month=2026-11&userId=1'))).json()).toEqual(usageBody);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/admin/ai/usage?month=2026-11', {});
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...usageBody, summary: { month: '2026-11' } }));
        expect((await usage(new Request('http://localhost/api/admin/ai/usage'))).status).toBe(502);
    });
    it.each(['?month=2026-13', '?month=202611', '?page=x'])('잘못된 조건 %s는 400이다', async search => {
        expect((await usage(new Request(`http://localhost/api/admin/ai/usage${search}`))).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('실패 기록은 허용한 필드만 넘기고 추천 조건을 넘기지 않는다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ items: [{ ...failure, conditions: { secret: 1 } }], totalElements: 1, page: 0, totalPages: 1 }));
        const response = await logs(new Request('http://localhost/api/admin/ai/logs?page=0'));
        const text = await response.text();
        expect(JSON.parse(text).items).toEqual([failure]);
        expect(text).not.toContain('secret');
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/admin/ai/logs?page=0', {});
    });
});
