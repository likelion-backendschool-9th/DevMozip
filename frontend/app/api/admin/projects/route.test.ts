import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
vi.mock('@/lib/auth/session', () => ({ getSession: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { getSession } from '@/lib/auth/session';
import { GET } from './route';
import { PUT } from './[id]/route';
import { PATCH } from './[id]/visibility/route';

const project = { id: 7, title: '공고', hostTag: 'host', hostNickname: '주최자', status: 'RECRUITING', isPublic: true, restricted: false,
    recruitmentEnd: '2026-11-30', developmentEnd: '2027-01-31', createdAt: '2026-11-10T10:00:00' };
const list = (search = '') => GET(new Request(`http://localhost/api/admin/projects${search}`));
const write = (method: string, path: string, body: string, origin = 'http://localhost') =>
    new Request(`http://localhost${path}`, { method, body, headers: { 'Content-Type': 'application/json', Origin: origin } });
const context = (id = '7') => ({ params: Promise.resolve({ id }) });
beforeEach(() => {
    vi.mocked(backendFetch).mockReset();
    vi.mocked(getSession).mockResolvedValue({ username: 'admin@devmozip.demo', role: 'ADMIN', isAdmin: true, token: 't' } as never);
});

describe('프로젝트 공고 관리 BFF (14.4·14.5)', () => {
    it('조건을 확인해 보내고 허용한 필드만 돌려준다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ items: [{ ...project, hostMemberId: 101 }], totalElements: 1, page: 0, totalPages: 1 }));
        const body = await (await list('?status=IN_PROGRESS&restricted=true&q=x')).json();
        expect(body.items[0]).toEqual(project);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/admin/projects?q=x&status=IN_PROGRESS&restricted=true', {});
    });
    it.each(['?status=DONE', '?restricted=yes'])('잘못된 조건 %s는 400이다', async search => {
        expect((await list(search)).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('삭제는 DELETE 조치만, 숨김은 boolean만 보낸다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response(null, { status: 204 }));
        expect((await PUT(write('PUT', '/api/admin/projects/7', '{"action":"DELETE"}'), context())).status).toBe(204);
        expect(backendFetch).toHaveBeenLastCalledWith('/api/v1/admin/projects/7', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: '{"action":"DELETE"}' });
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...project, restricted: true }));
        expect((await (await PATCH(write('PATCH', '/api/admin/projects/7/visibility', '{"restricted":true}'), context())).json()).restricted).toBe(true);
        expect((await PUT(write('PUT', '/api/admin/projects/7', '{"action":"HIDE"}'), context())).status).toBe(400);
        expect((await PATCH(write('PATCH', '/api/admin/projects/7/visibility', '{"restricted":"no"}'), context())).status).toBe(400);
        expect((await PUT(write('PUT', '/api/admin/projects/0', '{"action":"DELETE"}'), context('0'))).status).toBe(404);
        expect((await PUT(write('PUT', '/api/admin/projects/7', '{"action":"DELETE"}', 'http://evil.example'), context())).status).toBe(403);
        expect(backendFetch).toHaveBeenCalledTimes(2);
    });
    it('모집 중이 아닌 공고 삭제의 409 안내를 전달한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ message: '모집 중인 공고만 삭제할 수 있습니다.' }, { status: 409 }));
        const response = await PUT(write('PUT', '/api/admin/projects/7', '{"action":"DELETE"}'), context());
        expect(response.status).toBe(409);
        expect((await response.json()).message).toBe('모집 중인 공고만 삭제할 수 있습니다.');
    });
});
