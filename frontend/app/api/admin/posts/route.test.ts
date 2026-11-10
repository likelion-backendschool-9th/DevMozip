import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
vi.mock('@/lib/auth/session', () => ({ getSession: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { getSession } from '@/lib/auth/session';
import { GET } from './route';
import { PUT } from './[id]/route';
import { PATCH } from './[id]/visibility/route';

const post = { id: 10, type: 'POST', title: '스프링 질문', content: '본문', authorTag: 'author', authorNickname: '작성자', visibility: 'VISIBLE', createdAt: '2026-11-10T10:00:00' };
const comment = { id: 20, postId: 10, postTitle: '스프링 질문', content: '광고 댓글', authorTag: 'reader', authorNickname: '독자', createdAt: '2026-11-10T10:30:00' };
const page = (items: unknown[]) => ({ items, totalElements: items.length, page: 0, totalPages: 1 });
const list = (search = '') => GET(new Request(`http://localhost/api/admin/posts${search}`));
const write = (method: string, path: string, body: string, origin = 'http://localhost') =>
    new Request(`http://localhost${path}`, { method, body, headers: { 'Content-Type': 'application/json', Origin: origin } });
const context = (id = '10') => ({ params: Promise.resolve({ id }) });
beforeEach(() => {
    vi.mocked(backendFetch).mockReset();
    vi.mocked(getSession).mockResolvedValue({ username: 'admin@devmozip.demo', role: 'ADMIN', isAdmin: true, token: 't' } as never);
});

describe('게시글·댓글 목록 BFF (14.6)', () => {
    it('target에 맞는 형식으로 확인한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(page([{ ...post, extra: 1 }])));
        expect(await (await list('?visibility=RESTRICTED&q=%20봄%20')).json()).toEqual(page([post]));
        expect(backendFetch).toHaveBeenLastCalledWith('/api/v1/admin/posts?q=%EB%B4%84&visibility=RESTRICTED', {});
        vi.mocked(backendFetch).mockResolvedValue(Response.json(page([comment])));
        expect(await (await list('?target=COMMENT')).json()).toEqual(page([comment]));
        // 게시글 형식으로 온 응답을 댓글로 받지 않는다.
        vi.mocked(backendFetch).mockResolvedValue(Response.json(page([post])));
        expect((await list('?target=COMMENT')).status).toBe(502);
    });
    it.each(['?target=USER', '?visibility=PUBLIC', '?target=COMMENT&visibility=VISIBLE'])('잘못된 조건 %s는 400이다', async search => {
        expect((await list(search)).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
});

describe('관리자 삭제 BFF (14.6, D3)', () => {
    it('DELETE 조치만 target과 함께 보내고 204를 넘긴다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response(null, { status: 204 }));
        expect((await PUT(write('PUT', '/api/admin/posts/20?target=COMMENT', '{"action":"DELETE"}'), context('20'))).status).toBe(204);
        expect(backendFetch).toHaveBeenLastCalledWith('/api/v1/admin/posts/20?target=COMMENT',
            { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: '{"action":"DELETE"}' });
        await PUT(write('PUT', '/api/admin/posts/10', '{"action":"DELETE"}'), context());
        expect(vi.mocked(backendFetch).mock.calls[1][0]).toBe('/api/v1/admin/posts/10?target=POST');
    });
    it.each(['{"action":"HIDE"}', '{}', '{"action":"DELETE","reason":"x"}', '['])('본문 %s는 400이다', async body => {
        expect((await PUT(write('PUT', '/api/admin/posts/10', body), context())).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('잘못된 ID·target·다른 사이트 요청은 보내지 않는다', async () => {
        expect((await PUT(write('PUT', '/api/admin/posts/x', '{"action":"DELETE"}'), context('x'))).status).toBe(404);
        expect((await PUT(write('PUT', '/api/admin/posts/10?target=USER', '{"action":"DELETE"}'), context())).status).toBe(400);
        expect((await PUT(write('PUT', '/api/admin/posts/10', '{"action":"DELETE"}', 'http://evil.example'), context())).status).toBe(403);
        expect(backendFetch).not.toHaveBeenCalled();
    });
});

describe('관리자 숨김 BFF (14.7)', () => {
    it('boolean 값만 보내고 게시글 형식을 확인한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...post, visibility: 'RESTRICTED' }));
        const response = await PATCH(write('PATCH', '/api/admin/posts/10/visibility', '{"restricted":true}'), context());
        expect((await response.json()).visibility).toBe('RESTRICTED');
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/admin/posts/10/visibility',
            { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: '{"restricted":true}' });
    });
    it.each(['{"restricted":"true"}', '{}', '{"restricted":1}'])('본문 %s는 400이다', async body => {
        expect((await PATCH(write('PATCH', '/api/admin/posts/10/visibility', body), context())).status).toBe(400);
    });
    it('댓글은 숨김을 받지 않는다', async () => {
        expect((await PATCH(write('PATCH', '/api/admin/posts/20/visibility?target=COMMENT', '{"restricted":true}'), context('20'))).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
});
