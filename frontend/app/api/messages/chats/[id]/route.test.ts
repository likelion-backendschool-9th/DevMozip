import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { GET, POST } from './route';
import { PATCH } from './read/route';
import { directChatDetailFixture, directChatMessageFixture } from '@/tests/helpers/directChatFixture';

const URL_BASE = 'http://localhost/api/messages/chats';
const context = (id = '12') => ({ params: Promise.resolve({ id }) });
const write = (method: string, body: unknown, origin = 'http://localhost', contentType = 'application/json') => new Request(`${URL_BASE}/12`, {
    method, headers: { Host: 'localhost', Origin: origin, 'Content-Type': contentType }, body: typeof body === 'string' ? body : JSON.stringify(body) });
beforeEach(() => { vi.mocked(backendFetch).mockReset(); });

describe('메시지 조회 BFF', () => {
    it('공개 필드만 no-store로 반환한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...directChatDetailFixture, chatMemberId: 9 }));
        const response = await GET(new Request(`${URL_BASE}/12`), context());
        expect(response.status).toBe(200);
        expect(response.headers.get('cache-control')).toBe('no-store');
        expect(await response.json()).toEqual(directChatDetailFixture);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/messages/chats/12', {});
    });
    it('before, after, size만 전달한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(directChatDetailFixture));
        await GET(new Request(`${URL_BASE}/12?after=5&size=10&q=x&token=abc`), context());
        expect(vi.mocked(backendFetch).mock.calls[0][0]).toBe('/api/v1/messages/chats/12?after=5&size=10');
    });
    it.each(['abc', '0', '1/../2', '../../admin', '99999999999999999'])('잘못된 ID %s는 백엔드로 보내지 않는다', async id => {
        expect((await GET(new Request(`${URL_BASE}/x`), context(id))).status).toBe(404);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it.each([[401, 401], [404, 404], [400, 400], [500, 502]])('백엔드 상태 %i를 %i로 반환한다', async (status, expected) => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('internal details', { status }));
        const response = await GET(new Request(`${URL_BASE}/12`), context());
        expect(response.status).toBe(expected);
        expect(await response.text()).not.toContain('internal details');
    });
    it('잘못된 서버 응답은 502로 바꾼다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...directChatDetailFixture, messages: [{ id: 1 }] }));
        expect((await GET(new Request(`${URL_BASE}/12`), context())).status).toBe(502);
    });
});

describe('메시지 전송 BFF', () => {
    it('공백을 뺀 본문만 전달하고 201을 유지한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(directChatMessageFixture, { status: 201 }));
        const response = await POST(write('POST', { body: '  안녕하세요 ', senderId: 1 }), context());
        expect(response.status).toBe(201);
        expect(await response.json()).toEqual(directChatMessageFixture);
        const [path, init] = vi.mocked(backendFetch).mock.calls[0];
        expect(path).toBe('/api/v1/messages/chats/12');
        expect(init?.method).toBe('POST');
        expect(JSON.parse(String(init?.body))).toEqual({ body: '안녕하세요' });
    });
    it('2,000자까지 전달한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(directChatMessageFixture, { status: 201 }));
        expect((await POST(write('POST', { body: 'a'.repeat(2000) }), context())).status).toBe(201);
    });
    it.each([[''], ['   '], ['a'.repeat(2001)], [123], [null]])('길이·형식 위반 %j은 백엔드로 보내지 않는다', async body => {
        expect((await POST(write('POST', { body }), context())).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('깨진 JSON을 거부한다', async () => {
        expect((await POST(write('POST', '{깨진 JSON'), context())).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('다른 사이트와 JSON이 아닌 요청을 거부한다', async () => {
        expect((await POST(write('POST', { body: '안녕' }, 'https://evil.example'), context())).status).toBe(403);
        expect((await POST(write('POST', 'body=안녕', 'http://localhost', 'text/plain'), context())).status).toBe(415);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('잘못된 ID는 백엔드로 보내지 않는다', async () => {
        expect((await POST(write('POST', { body: '안녕' }), context('abc'))).status).toBe(404);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('상대 정지 409를 안내 문구로 바꾼다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('internal', { status: 409 }));
        const response = await POST(write('POST', { body: '안녕' }), context());
        expect(response.status).toBe(409);
        expect(await response.json()).toEqual({ message: '상대가 이용 정지 상태라 메시지를 보낼 수 없어요.' });
    });
    it('잘못된 응답과 연결 오류는 502다', async () => {
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json({ id: 'bad' }, { status: 201 }));
        expect((await POST(write('POST', { body: '안녕' }), context())).status).toBe(502);
        vi.mocked(backendFetch).mockRejectedValueOnce(new Error('http://internal-db:8080'));
        const response = await POST(write('POST', { body: '안녕' }), context());
        expect(response.status).toBe(502);
        expect(await response.text()).not.toContain('internal-db');
    });
});

describe('읽음 처리 BFF', () => {
    it('메시지 ID만 전달하고 본문 없는 204를 반환한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response(null, { status: 204 }));
        const response = await PATCH(write('PATCH', { lastReadMessageId: 340, memberId: 1 }), context());
        expect(response.status).toBe(204);
        expect(await response.text()).toBe('');
        expect(response.headers.get('cache-control')).toBe('no-store');
        const [path, init] = vi.mocked(backendFetch).mock.calls[0];
        expect(path).toBe('/api/v1/messages/chats/12/read');
        expect(init?.method).toBe('PATCH');
        expect(JSON.parse(String(init?.body))).toEqual({ lastReadMessageId: 340 });
    });
    it.each([[{}], [{ lastReadMessageId: '340' }], [{ lastReadMessageId: 0 }], [{ lastReadMessageId: 1.5 }], [{ lastReadMessageId: 2 ** 53 }]])('잘못된 입력 %j를 전달하지 않는다', async input => {
        expect((await PATCH(write('PATCH', input), context())).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('다른 사이트, JSON이 아닌 요청, 잘못된 ID를 거부한다', async () => {
        expect((await PATCH(write('PATCH', { lastReadMessageId: 1 }, 'https://evil.example'), context())).status).toBe(403);
        expect((await PATCH(write('PATCH', 'x', 'http://localhost', 'text/plain'), context())).status).toBe(415);
        expect((await PATCH(write('PATCH', { lastReadMessageId: 1 }), context('abc'))).status).toBe(404);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('다른 방의 메시지 400과 비참여 404를 반환한다', async () => {
        vi.mocked(backendFetch).mockResolvedValueOnce(new Response('internal', { status: 400 }));
        expect(await (await PATCH(write('PATCH', { lastReadMessageId: 1 }), context())).json()).toEqual({ message: '읽은 메시지를 확인해 주세요.' });
        vi.mocked(backendFetch).mockResolvedValueOnce(new Response('internal', { status: 404 }));
        expect((await PATCH(write('PATCH', { lastReadMessageId: 1 }), context())).status).toBe(404);
    });
});
