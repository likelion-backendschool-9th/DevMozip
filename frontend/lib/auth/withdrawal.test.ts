import { beforeEach, describe, expect, it, vi } from 'vitest';
const doubles = vi.hoisted(() => ({ fetch: vi.fn(), get: vi.fn(), set: vi.fn() }));
vi.mock('./backend', () => ({ backendFetch: doubles.fetch, readErrorMessage: async () => '탈퇴할 수 없습니다.' }));
vi.mock('next/headers', () => ({ cookies: async () => ({ get: doubles.get, set: doubles.set }), headers: async () => new Headers() }));
import { startKakaoAction, withdrawAccountAction } from './actions';
import { ACCESS_COOKIE, KAKAO_FLOW_COOKIE, KAKAO_SIGNUP_COOKIE, REFRESH_COOKIE, WITHDRAWAL_KAKAO_COOKIE } from './cookies';

beforeEach(() => { vi.resetAllMocks(); });

describe('회원 탈퇴 서버 액션', () => {
    it('확인하지 않은 요청은 서버에 보내지 않는다', async () => {
        expect(await withdrawAccountAction({ currentPassword: 'password', confirmed: false })).toHaveProperty('error');
        expect(doubles.fetch).not.toHaveBeenCalled();
    });
    it('성공하면 인증과 카카오 임시 쿠키를 모두 지운다', async () => {
        doubles.fetch.mockResolvedValue(new Response(null, { status: 204 }));
        expect(await withdrawAccountAction({ currentPassword: 'password', confirmed: true })).toBeNull();
        expect(doubles.fetch).toHaveBeenCalledWith('/api/v1/users/me', expect.objectContaining({ method: 'DELETE', body: JSON.stringify({ currentPassword: 'password', confirmed: true }) }));
        for (const name of [ACCESS_COOKIE, REFRESH_COOKIE, KAKAO_FLOW_COOKIE, KAKAO_SIGNUP_COOKIE, WITHDRAWAL_KAKAO_COOKIE]) {
            expect(doubles.set).toHaveBeenCalledWith(name, '', expect.objectContaining({ httpOnly: true, maxAge: 0 }));
        }
    });
    it('실패하면 로그인 쿠키는 유지한다', async () => {
        doubles.fetch.mockResolvedValue(new Response('{}', { status: 409 }));
        expect(await withdrawAccountAction({ currentPassword: 'password', confirmed: true })).toEqual({ error: '탈퇴할 수 없습니다.' });
        expect(doubles.set).not.toHaveBeenCalledWith(ACCESS_COOKIE, '', expect.anything());
    });
    it('카카오 코드는 같은 로그인 세션일 때만 보낸다', async () => {
        doubles.get.mockImplementation((name: string) => ({ value: name === ACCESS_COOKIE ? 'current' : JSON.stringify({ session: 'other', code: 'code' }) }));
        doubles.fetch.mockResolvedValue(new Response('{}', { status: 400 }));
        await withdrawAccountAction({ currentPassword: '', confirmed: true });
        expect(JSON.parse(doubles.fetch.mock.calls[0][1].body).kakaoCode).toBeUndefined();
        doubles.get.mockImplementation((name: string) => ({ value: name === ACCESS_COOKIE ? 'current' : JSON.stringify({ session: 'current', code: 'code' }) }));
        await withdrawAccountAction({ currentPassword: '', confirmed: true });
        expect(JSON.parse(doubles.fetch.mock.calls[1][1].body).kakaoCode).toBe('code');
    });
    it('탈퇴용 카카오 인증은 기존 로그인과 관계없이 다시 인증한다', async () => {
        doubles.fetch.mockResolvedValueOnce(new Response('{}', { status: 200 }))
            .mockResolvedValueOnce(Response.json({ authorizeUrl: 'https://kauth.kakao.com/oauth/authorize?state=state' }));
        const result = await startKakaoAction('withdraw');
        expect(result).toHaveProperty('url');
        if ('url' in result) expect(new URL(result.url).searchParams.get('prompt')).toBe('login');
        expect(doubles.set).toHaveBeenCalledWith(KAKAO_FLOW_COOKIE, expect.stringContaining('withdraw'), expect.objectContaining({ httpOnly: true }));
    });
});
