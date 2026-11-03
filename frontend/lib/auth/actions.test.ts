import { beforeEach, describe, expect, it, vi } from 'vitest';
const doubles = vi.hoisted(() => ({ fetch: vi.fn(), set: vi.fn(), get: vi.fn(), redirect: vi.fn((path: string) => { throw new Error(`redirect:${path}`); }) }));
vi.mock('./env', () => ({ authMode: () => 'server' }));
vi.mock('./backend', () => ({ backendFetch: doubles.fetch, readErrorMessage: async () => '인증 실패' }));
vi.mock('next/headers', () => ({ cookies: async () => ({ set: doubles.set, get: doubles.get }) }));
vi.mock('next/navigation', () => ({ redirect: doubles.redirect }));
import { loginAction, logoutAction } from './actions';
import { ACCESS_COOKIE, REFRESH_COOKIE } from './cookies';
beforeEach(() => { vi.clearAllMocks(); doubles.get.mockReturnValue({ value: 'refresh' }); });
describe('인증 서버 액션', () => {
    it('로그인 성공시에만 두 HttpOnly 쿠키를 쓰고 내부 경로로 이동한다', async () => {
        const access = `h.${Buffer.from(JSON.stringify({ sub: 'user', role: 'USER', exp: 9999999999 })).toString('base64url')}.s`;
        doubles.fetch.mockResolvedValue(Response.json({ accessToken: access, refreshToken: 'refresh' }));
        const form = new FormData(); form.set('username', 'user'); form.set('password', 'input'); form.set('next', '//outside.test');
        await expect(loginAction(form)).rejects.toThrow('redirect:/');
        expect(doubles.set).toHaveBeenCalledWith(ACCESS_COOKIE, access, expect.objectContaining({ httpOnly: true, path: '/', sameSite: 'lax' }));
        expect(doubles.set).toHaveBeenCalledWith(REFRESH_COOKIE, 'refresh', expect.objectContaining({ httpOnly: true, maxAge: 1209600 }));
    });
    it('인증 실패시 쿠키를 쓰지 않는다', async () => {
        doubles.fetch.mockResolvedValue(new Response('{}', { status: 401 }));
        expect(await loginAction(new FormData())).toEqual({ error: '인증 실패' });
        expect(doubles.set).not.toHaveBeenCalled();
    });
    it('로그아웃 성공 후 같은 옵션으로 두 쿠키를 만료시킨다', async () => {
        doubles.fetch.mockResolvedValue(new Response(null, { status: 204 }));
        await expect(logoutAction()).rejects.toThrow('redirect:/');
        expect(doubles.fetch).toHaveBeenCalledWith('/api/auth/logout', expect.objectContaining({ body: JSON.stringify({ refreshToken: 'refresh' }) }));
        expect(doubles.set).toHaveBeenCalledWith(ACCESS_COOKIE, '', expect.objectContaining({ maxAge: 0, httpOnly: true }));
        expect(doubles.set).toHaveBeenCalledWith(REFRESH_COOKIE, '', expect.objectContaining({ maxAge: 0, httpOnly: true }));
    });
    it('Redis 장애로 로그아웃 실패시 재시도할 수 있도록 쿠키를 유지한다', async () => {
        doubles.fetch.mockResolvedValue(new Response('{}', { status: 503 }));
        expect(await logoutAction()).toEqual({ error: '인증 실패' });
        expect(doubles.set).not.toHaveBeenCalled();
    });
});
