import { beforeEach, describe, expect, it, vi } from 'vitest';
const doubles = vi.hoisted(() => ({ fetch: vi.fn(), set: vi.fn(), get: vi.fn() }));
vi.mock('@/lib/auth/backend', () => ({ backendFetch: doubles.fetch }));
vi.mock('next/headers', () => ({ cookies: async () => ({ set: doubles.set, get: doubles.get }) }));
import { GET } from './route';
import { ACCESS_COOKIE, KAKAO_FLOW_COOKIE, KAKAO_SIGNUP_COOKIE, REFRESH_COOKIE, WITHDRAWAL_KAKAO_COOKIE } from '@/lib/auth/cookies';
import { isProtectedPath } from '@/lib/auth/paths';
import type { KakaoIntent } from '@/types/auth';

const access = `h.${Buffer.from(JSON.stringify({ sub: 'kakao@devmozip.test', role: 'USER', exp: 9999999999 })).toString('base64url')}.s`;
const flowCookie = (intent: KakaoIntent, next = '/') => JSON.stringify({ state: 'state', intent, next });
const startedAs = (intent: KakaoIntent, next?: string) => doubles.get.mockImplementation((name: string) => (name === KAKAO_FLOW_COOKIE ? { value: flowCookie(intent, next) } : undefined));
const callback = (query = 'code=code&state=state') => GET(new Request(`http://internal:3000/api/auth/kakao/callback?${query}`));
const locationOf = (response: Response) => { expect(response.status).toBe(302); return response.headers.get('location'); };
const wrote = (name: string) => doubles.set.mock.calls.filter(([cookie, value]) => cookie === name && value !== '');

beforeEach(() => { vi.clearAllMocks(); doubles.get.mockReset(); doubles.fetch.mockReset(); });

it('탈퇴 재인증 콜백은 탈퇴를 실행하지 않고 코드를 현재 세션의 HttpOnly 쿠키에 보관한다', async () => {
    doubles.get.mockImplementation((name: string) => name === KAKAO_FLOW_COOKIE ? { value: flowCookie('withdraw') } : name === ACCESS_COOKIE ? { value: access } : undefined);
    expect(locationOf(await callback())).toBe('/settings/privacy?withdrawal=kakao-ready');
    expect(doubles.fetch).not.toHaveBeenCalled();
    expect(doubles.set).toHaveBeenCalledWith(WITHDRAWAL_KAKAO_COOKIE, JSON.stringify({ code: 'code', session: access }), expect.objectContaining({ httpOnly: true, maxAge: 180 }));
});

describe('카카오 콜백: state 대조', () => {
    it('인증을 시작한 쿠키가 없으면 백엔드를 호출하지 않는다', async () => {
        doubles.get.mockReturnValue(undefined);
        expect(locationOf(await callback())).toBe('/login?kakao=invalid-state');
        expect(doubles.fetch).not.toHaveBeenCalled();
    });
    it('state가 다르면 시작한 화면으로 돌려보내고 백엔드를 호출하지 않는다', async () => {
        startedAs('signup');
        expect(locationOf(await callback('code=code&state=other'))).toBe('/signup?kakao=invalid-state');
        expect(doubles.fetch).not.toHaveBeenCalled();
    });
    it('state가 없거나 쿠키가 손상되면 거절한다', async () => {
        startedAs('login');
        expect(locationOf(await callback('code=code'))).toBe('/login?kakao=invalid-state');
        doubles.get.mockReturnValue({ value: '{broken' });
        expect(locationOf(await callback())).toBe('/login?kakao=invalid-state');
        doubles.get.mockReturnValue({ value: JSON.stringify({ state: 'state', intent: 'admin' }) });
        expect(locationOf(await callback())).toBe('/login?kakao=invalid-state');
        expect(doubles.fetch).not.toHaveBeenCalled();
    });
    it('사용한 state 쿠키는 결과와 관계없이 지워 다시 쓸 수 없다', async () => {
        startedAs('login');
        await callback('code=code&state=other');
        expect(doubles.set).toHaveBeenCalledWith(KAKAO_FLOW_COOKIE, '', expect.objectContaining({ maxAge: 0, httpOnly: true }));
    });
    it('콜백 주소는 비로그인 상태로도 들어올 수 있다', () => {
        expect(isProtectedPath('/api/auth/kakao/callback')).toBe(false);
    });
});

describe('카카오 콜백: 취소와 오류', () => {
    it('동의를 취소하면 시작한 화면에 취소 결과를 알린다', async () => {
        startedAs('link');
        expect(locationOf(await callback('error=access_denied&state=state'))).toBe('/settings/privacy?kakao=cancelled');
        expect(doubles.fetch).not.toHaveBeenCalled();
    });
    it('그 밖의 카카오 오류나 코드 없는 응답은 실패로 알린다', async () => {
        startedAs('login');
        expect(locationOf(await callback('error=server_error&state=state'))).toBe('/login?kakao=failed');
        startedAs('login');
        expect(locationOf(await callback('state=state'))).toBe('/login?kakao=failed');
        expect(doubles.fetch).not.toHaveBeenCalled();
    });
    it('이동 경로는 요청의 내부 주소를 쓰지 않는 상대 경로다', async () => {
        startedAs('login');
        expect(locationOf(await callback('error=access_denied&state=state'))).not.toContain('internal');
    });
});

describe('카카오 콜백: 로그인·가입', () => {
    it('등록된 회원은 두 HttpOnly 쿠키를 쓰고 처음 가려던 경로로 이동한다', async () => {
        startedAs('login', '/messages?userId=1');
        doubles.fetch.mockResolvedValue(Response.json({ status: 'LOGIN', accessToken: access, refreshToken: 'refresh', tokenType: 'Bearer' }));
        expect(locationOf(await callback())).toBe('/messages?userId=1');
        expect(doubles.fetch).toHaveBeenCalledWith('/api/v1/auth/kakao/callback?code=code&intent=login', expect.objectContaining({ signal: expect.any(AbortSignal) }));
        expect(doubles.set).toHaveBeenCalledWith(ACCESS_COOKIE, access, expect.objectContaining({ httpOnly: true, sameSite: 'lax' }));
        expect(doubles.set).toHaveBeenCalledWith(REFRESH_COOKIE, 'refresh', expect.objectContaining({ httpOnly: true, maxAge: 1209600 }));
    });
    it('저장된 next가 외부 주소면 메인으로 이동한다', async () => {
        startedAs('login', '//outside.test');
        doubles.fetch.mockResolvedValue(Response.json({ status: 'LOGIN', accessToken: access, refreshToken: 'refresh' }));
        expect(locationOf(await callback())).toBe('/');
    });
    it('인가 코드를 인코딩해 전달한다', async () => {
        startedAs('login');
        doubles.fetch.mockResolvedValue(new Response('{}', { status: 400 }));
        await callback(`code=${encodeURIComponent('a&intent=signup')}&state=state`);
        expect(doubles.fetch.mock.calls[0][0]).toBe('/api/v1/auth/kakao/callback?code=a%26intent%3Dsignup&intent=login');
    });
    it('토큰을 확인할 수 없으면 쿠키를 쓰지 않고 실패로 알린다', async () => {
        startedAs('login');
        doubles.fetch.mockResolvedValue(Response.json({ status: 'LOGIN', accessToken: 'bad', refreshToken: 'refresh' }));
        expect(locationOf(await callback())).toBe('/login?kakao=failed');
        expect(wrote(ACCESS_COOKIE)).toEqual([]);
        expect(wrote(REFRESH_COOKIE)).toEqual([]);
    });
    it.each(['login', 'signup'] as const)('%s에서 미가입이면 가입 토큰을 HttpOnly 쿠키에 두고 가입 폼으로 이동한다', async intent => {
        startedAs(intent);
        doubles.fetch.mockResolvedValue(Response.json({ status: 'SIGNUP_REQUIRED', kakaoSignupToken: 'signup-token' }));
        expect(locationOf(await callback())).toBe('/signup?kakao=signup');
        expect(doubles.fetch.mock.calls[0][0]).toContain(`intent=${intent}`);
        expect(doubles.set).toHaveBeenCalledWith(KAKAO_SIGNUP_COOKIE, 'signup-token', expect.objectContaining({ httpOnly: true, maxAge: 1800 }));
        expect(wrote(ACCESS_COOKIE)).toEqual([]);
    });
    it('가입에서 이미 등록된 회원이면 로그인시키지 않고 가입 화면에 알린다', async () => {
        startedAs('signup');
        doubles.fetch.mockResolvedValue(Response.json({ status: 'ALREADY_REGISTERED' }));
        expect(locationOf(await callback())).toBe('/signup?kakao=already-registered');
        expect(wrote(ACCESS_COOKIE)).toEqual([]);
        expect(wrote(KAKAO_SIGNUP_COOKIE)).toEqual([]);
    });
    it('이전에 남은 가입 토큰은 새 결과 전에 지운다', async () => {
        startedAs('login');
        doubles.fetch.mockResolvedValue(new Response('{}', { status: 400 }));
        await callback();
        expect(doubles.set).toHaveBeenCalledWith(KAKAO_SIGNUP_COOKIE, '', expect.objectContaining({ maxAge: 0 }));
    });
    it.each([[403, 'suspended'], [503, 'unavailable'], [400, 'failed'], [502, 'failed']])('백엔드 %i 응답은 %s로 알린다', async (status, result) => {
        startedAs('login');
        doubles.fetch.mockResolvedValue(new Response('{}', { status }));
        expect(locationOf(await callback())).toBe(`/login?kakao=${result}`);
    });
    it('알 수 없는 응답이나 연결 실패는 실패로 알린다', async () => {
        startedAs('signup');
        doubles.fetch.mockResolvedValue(Response.json({ status: 'UNKNOWN' }));
        expect(locationOf(await callback())).toBe('/signup?kakao=failed');
        startedAs('signup');
        doubles.fetch.mockRejectedValue(new Error('down'));
        expect(locationOf(await callback())).toBe('/signup?kakao=failed');
    });
});

describe('카카오 콜백: 연동·해제', () => {
    it('연동은 로그인 쿠키로 인가 코드만 전달하고 로그인 쿠키를 바꾸지 않는다', async () => {
        startedAs('link');
        doubles.fetch.mockResolvedValue(new Response(null, { status: 204 }));
        expect(locationOf(await callback())).toBe('/settings/privacy?kakao=linked');
        expect(doubles.fetch).toHaveBeenCalledWith('/api/v1/users/me/social-accounts/kakao', expect.objectContaining({ method: 'POST', body: JSON.stringify({ code: 'code' }), signal: expect.any(AbortSignal) }));
        expect(wrote(ACCESS_COOKIE)).toEqual([]);
    });
    it('이미 연동됐거나 다른 회원의 카카오 계정이면 충돌로 알린다', async () => {
        startedAs('link');
        doubles.fetch.mockResolvedValue(new Response('{}', { status: 409 }));
        expect(locationOf(await callback())).toBe('/settings/privacy?kakao=conflict');
    });
    it('해제는 DELETE로 인가 코드를 전달한다', async () => {
        startedAs('unlink');
        doubles.fetch.mockResolvedValue(new Response(null, { status: 204 }));
        expect(locationOf(await callback(`code=${encodeURIComponent('a&b')}&state=state`))).toBe('/settings/privacy?kakao=unlinked');
        expect(doubles.fetch).toHaveBeenCalledWith('/api/v1/users/me/social-accounts/kakao?code=a%26b', expect.objectContaining({ method: 'DELETE' }));
    });
    it('비밀번호가 없거나 다른 계정이면 해제 차단으로 알린다', async () => {
        startedAs('unlink');
        doubles.fetch.mockResolvedValue(new Response('{}', { status: 409 }));
        expect(locationOf(await callback())).toBe('/settings/privacy?kakao=unlink-blocked');
    });
    it.each(['link', 'unlink'] as const)('%s 중 로그인이 풀렸으면 로그인 후 설정 화면으로 돌아오게 한다', async intent => {
        startedAs(intent);
        doubles.fetch.mockResolvedValue(new Response('{}', { status: 401 }));
        expect(locationOf(await callback())).toBe('/login?next=%2Fsettings%2Fprivacy');
    });
    it.each(['link', 'unlink'] as const)('%s 중 정지되면 로그인 화면에 알린다', async intent => {
        startedAs(intent);
        doubles.fetch.mockResolvedValue(new Response('{}', { status: 403 }));
        expect(locationOf(await callback())).toBe('/login?kakao=suspended');
    });
    it('카카오 거절이나 연결 실패는 설정 화면에 실패로 알린다', async () => {
        startedAs('unlink');
        doubles.fetch.mockResolvedValue(new Response('{}', { status: 400 }));
        expect(locationOf(await callback())).toBe('/settings/privacy?kakao=failed');
        startedAs('link');
        doubles.fetch.mockRejectedValue(new Error('down'));
        expect(locationOf(await callback())).toBe('/settings/privacy?kakao=failed');
    });
});
