import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { GET, POST } from './route';
import { GET as unreadCount } from './unread-count/route';
import { directChatListFixture, directChatPeerFixture } from '@/tests/helpers/directChatFixture';

const URL_BASE = 'http://localhost/api/messages/chats';
const post = (body: unknown, origin = 'http://localhost', contentType = 'application/json') => POST(new Request(URL_BASE, {
    method: 'POST', headers: { Host: 'localhost', Origin: origin, 'Content-Type': contentType }, body: typeof body === 'string' ? body : JSON.stringify(body) }));
beforeEach(() => { vi.mocked(backendFetch).mockReset(); });

describe('대화 목록 BFF', () => {
    it('공개 필드만 no-store로 반환한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...directChatListFixture, items: [{ ...directChatListFixture.items[0],
            peer: { ...directChatPeerFixture, email: 'private@devmozip.test' } }] }));
        const response = await GET(new Request(URL_BASE));
        expect(response.status).toBe(200);
        expect(response.headers.get('cache-control')).toBe('no-store');
        expect(await response.json()).toEqual(directChatListFixture);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/messages/chats', {});
    });
    it('허용한 파라미터만 인코딩해 전달한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ items: [], nextCursor: null }));
        await GET(new Request(`${URL_BASE}?q=${encodeURIComponent('샘플 %&type=x')}&cursor=5&size=2&type=direct&memberId=1&token=abc`));
        const path = vi.mocked(backendFetch).mock.calls[0][0];
        const url = new URL(path, 'http://backend');
        expect(url.pathname).toBe('/api/v1/messages/chats');
        expect(Object.fromEntries(url.searchParams)).toEqual({ type: 'direct', q: '샘플 %&type=x', cursor: '5', size: '2' });
    });
    it('잘못된 서버 응답은 502로 바꾼다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ items: [{ id: 'bad' }], nextCursor: null }));
        expect((await GET(new Request(URL_BASE))).status).toBe(502);
    });
    it('JSON이 아닌 서버 응답도 502로 바꾼다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('<html>'));
        const response = await GET(new Request(URL_BASE));
        expect(response.status).toBe(502);
        expect(await response.text()).not.toContain('<html>');
    });
    it.each([[401, 401], [400, 400], [500, 502], [503, 502]])('백엔드 상태 %i를 %i로 바꾸고 내부 내용을 숨긴다', async (status, expected) => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('internal details', { status }));
        const response = await GET(new Request(URL_BASE));
        expect(response.status).toBe(expected);
        expect((await response.json()).message).toBeTruthy();
    });
    it('연결 오류에 내부 주소를 노출하지 않는다', async () => {
        vi.mocked(backendFetch).mockRejectedValue(new Error('http://internal-db:8080 secret'));
        const response = await GET(new Request(URL_BASE));
        expect(response.status).toBe(502);
        expect(await response.text()).not.toContain('internal-db');
    });
});

describe('대화 시작 BFF', () => {
    it('대상 태그만 전달하고 방 ID만 반환한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ id: 12, secret: true }));
        const response = await post({ targetSlug: 'sample_dev', memberId: 1, role: 'ADMIN' });
        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({ id: 12 });
        const [path, init] = vi.mocked(backendFetch).mock.calls[0];
        expect(path).toBe('/api/v1/messages/chats');
        expect(init?.method).toBe('POST');
        expect(JSON.parse(String(init?.body))).toEqual({ targetSlug: 'sample_dev' });
    });
    it('다른 사이트의 요청을 거부한다', async () => {
        expect((await post({ targetSlug: 'sample_dev' }, 'https://evil.example')).status).toBe(403);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('Origin이 없는 요청을 거부한다', async () => {
        const response = await POST(new Request(URL_BASE, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"targetSlug":"sample_dev"}' }));
        expect(response.status).toBe(403);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('JSON이 아닌 요청을 거부한다', async () => {
        expect((await post('targetSlug=sample_dev', 'http://localhost', 'application/x-www-form-urlencoded')).status).toBe(415);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it.each([['{깨진 JSON'], [[]], [{}], [{ targetSlug: 1 }], [{ targetSlug: '../admin' }], [{ targetSlug: '' }]])('잘못된 입력 %j를 전달하지 않는다', async input => {
        expect((await post(input)).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it.each([[400, '본인과는 대화를 시작할 수 없어요.'], [404, '대화 상대를 찾을 수 없어요.'], [401, '로그인이 만료됐어요. 다시 로그인해 주세요.']])('백엔드 %i의 안내 문구를 반환한다', async (status, message) => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('internal', { status }));
        const response = await post({ targetSlug: 'sample_dev' });
        expect(response.status).toBe(status);
        expect(await response.json()).toEqual({ message });
    });
});

describe('안 읽은 수 BFF', () => {
    it('숫자만 반환한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ count: 3, secret: true }));
        const response = await unreadCount();
        expect(response.headers.get('cache-control')).toBe('no-store');
        expect(await response.json()).toEqual({ count: 3 });
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/messages/chats/unread-count', {});
    });
    it('잘못된 값은 502로 바꾼다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ count: -1 }));
        expect((await unreadCount()).status).toBe(502);
    });
});
