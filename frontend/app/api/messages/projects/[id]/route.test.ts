import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { GET, POST } from './route';
import { PATCH as read } from './read/route';
import { POST as remove } from './delete/route';
import { GET as unreadCount } from '../unread-count/route';
import { GET as listChats } from '../../chats/route';
import { projectChatDetailFixture, projectChatListFixture, projectMessageFixture } from '@/tests/helpers/projectChatFixture';

const BASE = 'http://localhost/api/messages/projects';
const context = (id: string) => ({ params: Promise.resolve({ id }) });
const write = (method: string, url: string, body: unknown, origin = 'http://localhost', contentType = 'application/json') => new Request(url, {
    method, headers: { Host: 'localhost', Origin: origin, 'Content-Type': contentType }, body: typeof body === 'string' ? body : JSON.stringify(body) });
beforeEach(() => { vi.mocked(backendFetch).mockReset(); });

describe('프로젝트 채팅 조회 BFF', () => {
    it('허용한 파라미터만 넘기고 계약에 없는 필드는 빼고 반환한다', async () => {
        const detail = projectChatDetailFixture({ messages: [projectMessageFixture(10, 2)] });
        vi.mocked(backendFetch).mockResolvedValue(Response.json({
            ...detail, secret: 'x', members: detail.members.map(member => ({ ...member, email: 'private@devmozip.test' })) }));
        const response = await GET(new Request(`${BASE}/5?after=3&size=2&memberId=1`), context('5'));
        expect(response.status).toBe(200);
        expect(response.headers.get('cache-control')).toBe('no-store');
        expect(await response.json()).toEqual(detail);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/messages/projects/5?after=3&size=2', {});
    });

    it('숫자가 아닌 ID는 백엔드를 부르지 않고 404다', async () => {
        for (const id of ['pocket', '0', '-1', '1.5', '99999999999999999999']) {
            expect((await GET(new Request(`${BASE}/${id}`), context(id))).status).toBe(404);
        }
        expect(backendFetch).not.toHaveBeenCalled();
    });

    it('입장할 수 없으면 404 안내를, 잘못된 응답은 502를 돌려준다', async () => {
        vi.mocked(backendFetch).mockResolvedValueOnce(new Response('internal', { status: 404 }));
        const refused = await GET(new Request(`${BASE}/5`), context('5'));
        expect(refused.status).toBe(404);
        expect((await refused.json()).message).toBe('프로젝트 채팅에 입장할 수 없어요.');
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json({ ...projectChatDetailFixture(), status: 'UNKNOWN' }));
        expect((await GET(new Request(`${BASE}/5`), context('5'))).status).toBe(502);
    });
});

describe('프로젝트 채팅 전송·읽음·삭제 BFF', () => {
    it('같은 사이트의 JSON 요청만 받고 다듬은 본문만 넘긴다', async () => {
        expect((await POST(write('POST', `${BASE}/5`, { body: '안녕' }, 'https://evil.example'), context('5'))).status).toBe(403);
        expect((await POST(write('POST', `${BASE}/5`, 'body=안녕', 'http://localhost', 'text/plain'), context('5'))).status).toBe(415);
        expect((await POST(write('POST', `${BASE}/5`, { body: '   ' }), context('5'))).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
        vi.mocked(backendFetch).mockResolvedValue(Response.json(projectMessageFixture(10, 1), { status: 201 }));
        const response = await POST(write('POST', `${BASE}/5`, { body: '  안녕  ', senderId: 9 }), context('5'));
        expect(response.status).toBe(201);
        expect(JSON.parse(vi.mocked(backendFetch).mock.calls[0][1]!.body as string)).toEqual({ body: '안녕' });
    });

    it('종료된 프로젝트의 전송(409)은 안내 문구로 바꾼다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('internal', { status: 409 }));
        const response = await POST(write('POST', `${BASE}/5`, { body: '안녕' }), context('5'));
        expect(response.status).toBe(409);
        expect((await response.json()).message).toBe('종료된 프로젝트 채팅에는 메시지를 보낼 수 없어요.');
    });

    it('읽음은 양의 정수 메시지 ID만 넘긴다', async () => {
        expect((await read(write('PATCH', `${BASE}/5/read`, { lastReadMessageId: '3' }), context('5'))).status).toBe(400);
        vi.mocked(backendFetch).mockResolvedValue(new Response(null, { status: 204 }));
        expect((await read(write('PATCH', `${BASE}/5/read`, { lastReadMessageId: 3 }), context('5'))).status).toBe(204);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/messages/projects/5/read', expect.objectContaining({ method: 'PATCH' }));
    });

    it('삭제는 본문 없이 POST로 넘기고 권한·시점 오류를 안내한다', async () => {
        expect((await remove(write('POST', `${BASE}/5/delete`, {}, 'https://evil.example'), context('5'))).status).toBe(403);
        vi.mocked(backendFetch).mockResolvedValueOnce(new Response(null, { status: 204 }));
        expect((await remove(write('POST', `${BASE}/5/delete`, {}), context('5'))).status).toBe(204);
        expect(backendFetch).toHaveBeenLastCalledWith('/api/v1/messages/projects/5/delete', { method: 'POST' });
        vi.mocked(backendFetch).mockResolvedValueOnce(new Response('internal', { status: 403 }));
        expect((await (await remove(write('POST', `${BASE}/5/delete`, {}), context('5'))).json()).message).toBe('주최자만 프로젝트 채팅방을 삭제할 수 있어요.');
        vi.mocked(backendFetch).mockResolvedValueOnce(new Response('internal', { status: 409 }));
        expect((await (await remove(write('POST', `${BASE}/5/delete`, {}), context('5'))).json()).message).toBe('프로젝트가 종료된 뒤에 채팅방을 삭제할 수 있어요.');
    });
});

describe('프로젝트 채팅 목록·안 읽은 수 BFF', () => {
    it('type=project 목록은 프로젝트 형식으로 확인해 전달한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...projectChatListFixture, items: projectChatListFixture.items.map(item => ({ ...item, hostEmail: 'x' })) }));
        const response = await listChats(new Request('http://localhost/api/messages/chats?type=project&q=샘플'));
        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(projectChatListFixture);
        expect(vi.mocked(backendFetch).mock.calls[0][0]).toContain('type=project');
    });

    it('안 읽은 수를 전달한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ count: 3, extra: true }));
        expect(await (await unreadCount()).json()).toEqual({ count: 3 });
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/messages/projects/unread-count', {});
    });
});
