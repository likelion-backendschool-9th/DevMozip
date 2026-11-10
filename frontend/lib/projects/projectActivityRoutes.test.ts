import { beforeEach, describe, expect, it, vi } from 'vitest';
const mock = vi.hoisted(() => ({ guard: vi.fn(), proxy: vi.fn() }));
vi.mock('@/lib/projects/projectProxy', () => ({ projectGuard: mock.guard, proxyProject: mock.proxy, projectError: (status: number, message: string) => Response.json({ message }, { status }) }));
import { GET } from '@/app/api/projects/activity/[slug]/route';
beforeEach(() => { vi.clearAllMocks(); mock.guard.mockResolvedValue(null); mock.proxy.mockResolvedValue(Response.json({ items: [] })); });
const context = (slug: string) => ({ params: Promise.resolve({ slug }) });
describe('프로필 프로젝트 활동 BFF', () => {
    it('다른 사용자 공개 조회는 페이지 정보만 전달한다', async () => {
        await GET(new Request('http://localhost?userId=999&page=2&size=10&includePrivate=true'), context('sample-tag'));
        expect(mock.guard).not.toHaveBeenCalled();
        expect(mock.proxy).toHaveBeenCalledWith('/api/v1/users/sample-tag/projects?page=2&size=10');
    });
    it('본인 조회의 인증 실패 시 요청하지 않는다', async () => {
        mock.guard.mockResolvedValue(Response.json({}, { status: 401 }));
        expect((await GET(new Request('http://localhost'), context('me'))).status).toBe(401);
        expect(mock.proxy).not.toHaveBeenCalled();
    });
    it('본인 조회는 me 경로를 유지한다', async () => {
        await GET(new Request('http://localhost'), context('me'));
        expect(mock.guard).toHaveBeenCalled();
        expect(mock.proxy).toHaveBeenCalledWith('/api/v1/users/me/projects?');
    });
    it.each(['../me', '..', '.', 'user?x', 'user#x', 'user\\name', 'x'.repeat(101)])('경로를 벗어나는 태그를 거절한다: %s', async slug => {
        expect((await GET(new Request('http://localhost'), context(slug))).status).toBe(400);
        expect(mock.proxy).not.toHaveBeenCalled();
    });
});
