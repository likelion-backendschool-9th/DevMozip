import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { GET } from './route';
import { PATCH as read } from './[id]/read/route';
import { PATCH as readAll } from './read-all/route';

const item = { id: 31, type: 'PROJECT_STATUS_CHANGED', title: '프로젝트 상태 변경', body: '본문', actionUrl: '/projects/12', projectId: 12,
    read: false, resolved: false, review: null, createdAt: '2026-11-10T10:00:00' };
const page = (extra: Record<string, unknown> = {}) => ({ items: [item], page: 0, size: 20, hasNext: false, unreadCount: 1, ...extra });
const get = (search = '') => GET(new Request(`http://localhost/api/notifications${search}`));
const context = (id = '31') => ({ params: Promise.resolve({ id }) });
// origin이 null이면 Origin 헤더를 보내지 않는다.
const patch = (path: string, init: RequestInit = {}, origin: string | null = 'http://localhost') =>
    new Request(`http://localhost${path}`, { method: 'PATCH', ...init, headers: { ...(origin ? { Origin: origin } : {}), ...init.headers } });
const json = (body: string) => ({ headers: { 'Content-Type': 'application/json' }, body });
// beforeEach가 함수를 돌려주면 Vitest가 정리 함수로 부르므로 mockReset의 반환값을 돌려주지 않는다.
beforeEach(() => { vi.mocked(backendFetch).mockReset(); });

describe('알림 목록 BFF', () => {
    it('확인한 status·page·size만 백엔드로 보내고 허용한 필드만 no-store로 돌려준다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(page({ items: [{ ...item, token: 'secret' }], email: 'x' })));
        const response = await get('?status=unread&page=0&size=20&userId=9&recipientId=9');
        expect(response.status).toBe(200);
        expect(response.headers.get('cache-control')).toBe('no-store');
        expect(await response.json()).toEqual(page());
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/notifications?status=unread&page=0&size=20', {});
    });
    it.each(['?status=bad', '?page=-1', '?size=101', '?status=read&status=all'])('잘못된 조건 %s는 백엔드로 보내지 않는다', async search => {
        expect((await get(search)).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('요청과 다른 페이지 응답이나 손상된 응답은 502다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(page({ page: 1 })));
        expect((await get()).status).toBe(502);
        vi.mocked(backendFetch).mockResolvedValue(Response.json(page({ items: [{ ...item, read: 'no' }] })));
        expect((await get()).status).toBe(502);
    });
    it('외부·프로토콜 상대 링크는 null로 바꾼다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(page({ items: [{ ...item, actionUrl: '//evil.example' }] })));
        expect((await (await get()).json()).items[0].actionUrl).toBeNull();
    });
    it.each([[400, 400], [401, 401], [403, 403], [404, 404], [409, 502], [500, 502], [503, 502]])('백엔드 %s를 %s로 바꾸고 내부 본문을 숨긴다', async (backend, expected) => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('internal stack trace', { status: backend }));
        const response = await get();
        expect(response.status).toBe(expected);
        const text = await response.text();
        expect(text).not.toContain('internal');
        expect(JSON.parse(text).message).toEqual(expect.any(String));
    });
    it('비로그인은 백엔드의 401을 안내 문구로 전달한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ message: 'Full authentication is required' }, { status: 401 }));
        const response = await get();
        expect(response.status).toBe(401);
        expect((await response.json()).message).toBe('로그인이 필요해요. 다시 로그인해 주세요.');
    });
    it('연결 오류는 502이며 내부 주소를 드러내지 않는다', async () => {
        vi.mocked(backendFetch).mockRejectedValue(new Error('http://internal:8080 secret'));
        const response = await get();
        expect(response.status).toBe(502);
        expect(await response.text()).not.toContain('secret');
    });
});

describe('알림 읽음 BFF', () => {
    it('본문이 없으면 읽음, read 값이 있으면 그 값만 백엔드로 보낸다', async () => {
        vi.mocked(backendFetch).mockImplementation(async () => Response.json({ id: 31, read: true, unreadCount: 0 }));
        expect((await read(patch('/api/notifications/31/read'), context())).status).toBe(200);
        expect(backendFetch).toHaveBeenLastCalledWith('/api/v1/notifications/31/read',
            { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: '{"read":true}' });
        vi.mocked(backendFetch).mockImplementation(async () => Response.json({ id: 31, read: false, unreadCount: 1 }));
        const response = await read(patch('/api/notifications/31/read', json('{"read":false}')), context());
        expect(await response.json()).toEqual({ id: 31, read: false, unreadCount: 1 });
        expect(backendFetch).toHaveBeenLastCalledWith('/api/v1/notifications/31/read',
            { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: '{"read":false}' });
    });
    it.each(['{', '{}', '{"read":null}', '{"read":"true"}', '[true]', '{"read":true,"userId":9}'])('잘못된 본문 %s는 400이다', async body => {
        expect((await read(patch('/api/notifications/31/read', json(body)), context())).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('JSON이 아닌 본문은 400이다', async () => {
        expect((await read(patch('/api/notifications/31/read', { headers: { 'Content-Type': 'text/plain' }, body: 'read' }), context())).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it.each(['0', '-1', 'abc', '1.5', '../1'])('잘못된 ID %s는 백엔드로 보내지 않는다', async id => {
        expect((await read(patch(`/api/notifications/${id}/read`), context(id))).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it.each(['http://evil.example', 'https://localhost', 'null', null])('다른 출처 %s의 쓰기는 403이다', async origin => {
        expect((await read(patch('/api/notifications/31/read', {}, origin), context())).status).toBe(403);
        expect((await readAll(patch('/api/notifications/read-all', {}, origin))).status).toBe(403);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('Next 내부 URL과 브라우저 주소가 달라도 브라우저가 보낸 Host로 같은 사이트를 판단한다', async () => {
        vi.mocked(backendFetch).mockImplementation(async () => Response.json({ unreadCount: 0 }));
        const docker = new Request('http://0.0.0.0:3000/api/notifications/read-all', { method: 'PATCH', headers: { Host: 'localhost:3000', Origin: 'http://localhost:3000' } });
        expect((await readAll(docker)).status).toBe(200);
    });
    it('다른 사람의 알림(404)과 응답 ID 불일치를 구분한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ message: '알림을 찾을 수 없습니다.' }, { status: 404 }));
        const missing = await read(patch('/api/notifications/31/read'), context());
        expect(missing.status).toBe(404);
        expect((await missing.json()).message).toBe('알림을 찾을 수 없어요.');
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ id: 32, read: true, unreadCount: 0 }));
        expect((await read(patch('/api/notifications/31/read'), context())).status).toBe(502);
    });
});

describe('모두 읽음 BFF', () => {
    it('본인 알림 모두 읽음을 백엔드로 보내고 안 읽음 수를 돌려준다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ unreadCount: 0 }));
        const response = await readAll(patch('/api/notifications/read-all', { body: '{"recipientId":9}' }));
        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({ unreadCount: 0 });
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/notifications/read-all', { method: 'PATCH' });
    });
});
