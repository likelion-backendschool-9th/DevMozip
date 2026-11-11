import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
vi.mock('@/lib/auth/session', () => ({ getSession: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { getSession } from '@/lib/auth/session';
import { GET } from './route';

const dashboard = { members: 4, premiumMembers: 1, suspendedMembers: 1, projects: 1, posts: 1, pendingReports: 2, aiRecommendationsThisMonth: 5,
    recentActions: [{ type: 'SANCTION', actorNickname: '운영자', summary: '회원 정지 · 박경고', createdAt: '2026-11-10T10:00:00' }] };
const get = () => GET(new Request('http://localhost/api/admin/dashboard'));
beforeEach(() => {
    vi.mocked(backendFetch).mockReset();
    vi.mocked(getSession).mockResolvedValue({ username: 'admin@devmozip.demo', role: 'ADMIN', isAdmin: true, token: 't' } as never);
});

describe('대시보드 BFF (14.1)', () => {
    it('수치와 최근 변경의 형식을 확인한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...dashboard, secret: 1 }));
        expect(await (await get()).json()).toEqual(dashboard);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/admin/dashboard', {});
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...dashboard, members: '4' }));
        expect((await get()).status).toBe(502);
    });
    it('관리자가 아니면 403이다', async () => {
        vi.mocked(getSession).mockResolvedValue({ username: 'm@test', role: 'USER', isAdmin: false, token: 't' } as never);
        expect((await get()).status).toBe(403);
        expect(backendFetch).not.toHaveBeenCalled();
    });
});
