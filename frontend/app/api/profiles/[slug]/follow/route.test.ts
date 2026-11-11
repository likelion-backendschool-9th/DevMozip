import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { DELETE, GET, POST } from './route';
import { GET as followers } from '../followers/route';
import { GET as following } from '../following/route';
import { PUT } from '../notify/route';

const context = (slug = 'dev-one') => ({ params: Promise.resolve({ slug }) });
const request = (method: string, init: RequestInit = {}, origin = 'http://localhost', path = '/api/profiles/dev-one/follow') =>
    new Request(`http://localhost${path}`, { method, ...init, headers: { Origin: origin, ...init.headers } });
const status = { followerCount: 2, followingCount: 1, followed: true, activityNotificationEnabled: false };
const result = { followed: true, activityNotificationEnabled: false, followerCount: 3 };
const list = { items: [], page: 0, size: 20, hasNext: false, totalCount: 0 };
const notify = (body: unknown, contentType = 'application/json', origin = 'http://localhost') =>
    PUT(request('PUT', { headers: { 'Content-Type': contentType }, body: JSON.stringify(body) }, origin, '/api/profiles/dev-one/notify'), context());
beforeEach(() => vi.mocked(backendFetch).mockReset());

describe('팔로우 BFF', () => {
    it('팔로우 상태를 공개로 조회하고 필요한 필드만 no-store로 돌려준다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...status, email: 'private' }));
        const response = await GET(request('GET'), context());
        expect(response.status).toBe(200);
        expect(response.headers.get('cache-control')).toBe('no-store');
        expect(await response.json()).toEqual(status);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/users/dev-one/follow', {});
    });
    it('팔로우·언팔로우는 같은 사이트 요청만 백엔드에 전달하고 사용자 ID를 보내지 않는다', async () => {
        vi.mocked(backendFetch).mockImplementation(async () => Response.json(result));
        expect((await POST(request('POST', { body: '{"followerId":9}' }), context())).status).toBe(200);
        expect(backendFetch).toHaveBeenLastCalledWith('/api/v1/users/dev-one/follow', { method: 'POST' });
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...result, followed: false, followerCount: 2 }));
        expect((await DELETE(request('DELETE'), context())).status).toBe(200);
        expect(backendFetch).toHaveBeenLastCalledWith('/api/v1/users/dev-one/follow', { method: 'DELETE' });
        expect((await POST(request('POST', {}, 'http://evil.example'), context())).status).toBe(403);
        expect((await DELETE(request('DELETE', {}, 'http://evil.example'), context())).status).toBe(403);
        expect(backendFetch).toHaveBeenCalledTimes(2);
    });
    it('Next 내부 URL과 브라우저 주소가 달라도 브라우저가 보낸 Host로 같은 사이트를 판단한다', async () => {
        // Docker 개발 서버(--hostname 0.0.0.0)에서는 request.url의 호스트가 브라우저 주소와 다르다.
        const docker = (method: string, origin?: string, path = '/api/profiles/dev-one/follow', init: RequestInit = {}) => new Request(
            `http://0.0.0.0:3000${path}`, { method, ...init, headers: { Host: 'localhost:3000', ...(origin ? { Origin: origin } : {}), ...init.headers } });
        vi.mocked(backendFetch).mockImplementation(async () => Response.json(result));
        expect((await POST(docker('POST', 'http://localhost:3000'), context())).status).toBe(200);
        expect((await DELETE(docker('DELETE', 'http://localhost:3000'), context())).status).toBe(200);
        vi.mocked(backendFetch).mockImplementation(async () => Response.json({ activityNotificationEnabled: true }));
        const body = { headers: { 'Content-Type': 'application/json' }, body: '{"activityNotificationEnabled":true}' };
        expect((await PUT(docker('PUT', 'http://localhost:3000', '/api/profiles/dev-one/notify', body), context())).status).toBe(200);
        expect(backendFetch).toHaveBeenCalledTimes(3);
        for (const origin of ['http://evil.example', 'https://localhost:3000', 'http://localhost:3001', 'null', undefined])
            expect((await POST(docker('POST', origin), context())).status).toBe(403);
        expect(backendFetch).toHaveBeenCalledTimes(3);
    });
    it.each(['../auth/me', '//example.com', 'bad?tag', ''])('잘못된 태그 %s를 전달하지 않는다', async slug => {
        expect((await GET(request('GET'), context(slug))).status).toBe(400);
        expect((await POST(request('POST'), context(slug))).status).toBe(400);
        expect((await followers(request('GET'), context(slug))).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it.each([[400, 400], [401, 401], [403, 403], [404, 404], [409, 409], [500, 502], [503, 502]])('백엔드 %s를 %s로 바꾸고 내부 본문을 숨긴다', async (backend, expected) => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('internal stack trace', { status: backend }));
        const response = await POST(request('POST'), context());
        expect(response.status).toBe(expected);
        const text = await response.text();
        expect(text).not.toContain('internal');
        expect(JSON.parse(text).message).toEqual(expect.any(String));
    });
    it('연결 오류와 손상된 응답은 502다', async () => {
        vi.mocked(backendFetch).mockRejectedValue(new Error('http://internal:8080 secret'));
        const failed = await GET(request('GET'), context());
        expect(failed.status).toBe(502);
        expect(await failed.text()).not.toContain('secret');
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ followed: false, activityNotificationEnabled: true, followerCount: 1 }));
        expect((await DELETE(request('DELETE'), context())).status).toBe(502);
    });
    it('비공개 계정의 403은 계약 문구만 그대로 넘기고 정지 회원 403과 구분한다', async () => {
        const privateAccount = () => Response.json({ status: 403, message: '비공개 계정입니다.' }, { status: 403 });
        const calls = [
            () => GET(request('GET'), context()),
            () => POST(request('POST'), context()),
            () => DELETE(request('DELETE'), context()),
            () => followers(request('GET', {}, 'http://localhost', '/api/profiles/dev-one/followers'), context()),
            () => following(request('GET', {}, 'http://localhost', '/api/profiles/dev-one/following?q=java'), context()),
        ];
        for (const call of calls) {
            vi.mocked(backendFetch).mockResolvedValueOnce(privateAccount());
            const response = await call();
            expect(response.status).toBe(403);
            expect((await response.json()).message).toBe('비공개 계정입니다.');
        }
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json({ message: '이 계정 상태에서는 이용할 수 없습니다.' }, { status: 403 }));
        const suspended = await POST(request('POST'), context());
        expect(suspended.status).toBe(403);
        expect((await suspended.json()).message).toBe('이 계정으로는 팔로우 기능을 이용할 수 없어요.');
    });
});

describe('팔로워·팔로잉 목록 BFF', () => {
    it('확인한 조건만 백엔드에 넘긴다', async () => {
        vi.mocked(backendFetch).mockImplementation(async () => Response.json(list));
        expect((await followers(request('GET', {}, 'http://localhost', '/api/profiles/dev-one/followers?q=%20react%20&available=true&userId=3'), context())).status).toBe(200);
        expect(backendFetch).toHaveBeenLastCalledWith('/api/v1/users/dev-one/followers?q=react&available=true&page=0&size=20', {});
        expect((await following(request('GET', {}, 'http://localhost', '/api/profiles/dev-one/following'), context())).status).toBe(200);
        expect(backendFetch).toHaveBeenLastCalledWith('/api/v1/users/dev-one/following?page=0&size=20', {});
    });
    it.each(['page=-1', 'size=101', 'available=yes', `q=${'a'.repeat(101)}`])('잘못된 조건 %s는 400이다', async query => {
        const response = await followers(request('GET', {}, 'http://localhost', `/api/profiles/dev-one/followers?${query}`), context());
        expect(response.status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('요청한 페이지와 다른 응답은 502다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...list, page: 2 }));
        expect((await following(request('GET', {}, 'http://localhost', '/api/profiles/dev-one/following?page=1'), context())).status).toBe(502);
    });
});

describe('알림 설정 BFF', () => {
    it('검증한 본문만 전달한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ activityNotificationEnabled: true }));
        const response = await notify({ activityNotificationEnabled: true, followerId: 9 });
        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({ activityNotificationEnabled: true });
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/users/dev-one/notify',
            { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: '{"activityNotificationEnabled":true}' });
    });
    it.each([{}, { activityNotificationEnabled: 'true' }, { activityNotificationEnabled: null }, []])('잘못된 본문 %j는 400이다', async body => {
        expect((await notify(body)).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('JSON이 아니거나 다른 사이트 요청은 막는다', async () => {
        expect((await notify({ activityNotificationEnabled: true }, 'text/plain')).status).toBe(415);
        expect((await notify({ activityNotificationEnabled: true }, 'application/json', 'http://evil.example')).status).toBe(403);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('팔로우하지 않은 사용자의 설정 409와 요청과 다른 응답 502', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('{}', { status: 409 }));
        const conflict = await notify({ activityNotificationEnabled: true });
        expect(conflict.status).toBe(409);
        expect((await conflict.json()).message).toBe('팔로우한 개발자의 알림만 설정할 수 있어요.');
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ activityNotificationEnabled: false }));
        expect((await notify({ activityNotificationEnabled: true })).status).toBe(502);
    });
});
