import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NextRequest } from 'next/server';
vi.mock('./refresh', () => ({ refreshAccessToken: vi.fn() }));
import { refreshAccessToken } from './refresh';
import { proxy } from '@/proxy';
import { ACCESS_COOKIE, REFRESH_COOKIE } from './cookies';
const jwt = (role: string, exp = 9999999999) => `header.${Buffer.from(JSON.stringify({ sub: 'user', role, exp })).toString('base64url')}.sig`;
const request = (path: string, cookie = '') => new NextRequest(`https://devmozip.test${path}`, { headers: { cookie } });
beforeEach(() => { vi.mocked(refreshAccessToken).mockReset(); });
describe('BFF proxy', () => {
    it('보호 화면에 next 쿼리를 보존해 로그인으로 보낸다', async () => {
        const response = await proxy(request('/messages?userId=user'));
        const location = new URL(response.headers.get('location')!);
        expect(location.pathname).toBe('/login');
        expect(location.searchParams.get('next')).toBe('/messages?userId=user');
    });
    it.each(['USER', 'PREMIUM'])('%s의 관리자 접근을 막는다', async role => {
        const response = await proxy(request('/admin/users', `${ACCESS_COOKIE}=${jwt(role)}`));
        expect(new URL(response.headers.get('location')!).pathname).toBe('/');
    });
    it('PREMIUM 토큰은 로그인 상태로 보호 화면을 연다', async () => {
        const response = await proxy(request('/my-projects', `${ACCESS_COOKIE}=${jwt('PREMIUM')}`));
        expect(response.headers.get('location')).toBeNull();
        expect(refreshAccessToken).not.toHaveBeenCalled();
    });
    it('재발급 토큰을 이번 요청과 응답 쿠키에 모두 전달한다', async () => {
        const access = jwt('ADMIN');
        vi.mocked(refreshAccessToken).mockResolvedValue(access);
        const response = await proxy(request('/admin', `${ACCESS_COOKIE}=${jwt('ADMIN', 1)}; ${REFRESH_COOKIE}=refresh`));
        expect(response.status).toBe(200);
        expect(response.headers.get('x-middleware-request-cookie')).toContain(access);
        expect(response.cookies.get(ACCESS_COOKIE)?.value).toBe(access);
        expect(response.headers.get('set-cookie')).toContain('HttpOnly');
    });
    it('재발급 실패시 요청 쿠키와 브라우저 쿠키를 모두 지운다', async () => {
        vi.mocked(refreshAccessToken).mockResolvedValue(null);
        const response = await proxy(request('/feeds', `${ACCESS_COOKIE}=bad; ${REFRESH_COOKIE}=bad`));
        expect(response.headers.get('x-middleware-request-cookie')).not.toContain('bad');
        expect(response.cookies.get(ACCESS_COOKIE)?.value).toBe('');
        expect(response.cookies.get(REFRESH_COOKIE)?.value).toBe('');
    });
    it('헬스체크에는 재발급이 끼어들지 않는다', async () => {
        await proxy(request('/api/health', `${REFRESH_COOKIE}=refresh`));
        expect(refreshAccessToken).not.toHaveBeenCalled();
    });
});
