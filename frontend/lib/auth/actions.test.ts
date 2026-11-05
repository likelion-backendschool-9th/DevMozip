import { beforeEach, describe, expect, it, vi } from 'vitest';
const doubles = vi.hoisted(() => ({ fetch: vi.fn(), set: vi.fn(), get: vi.fn(), redirect: vi.fn((path: string) => { throw new Error(`redirect:${path}`); }) }));
vi.mock('./backend', () => ({ backendFetch: doubles.fetch, readErrorMessage: async () => '인증 실패' }));
vi.mock('next/headers', () => ({ cookies: async () => ({ set: doubles.set, get: doubles.get }) }));
vi.mock('next/navigation', () => ({ redirect: doubles.redirect }));
import { checkAvailabilityAction, kakaoAccountStatusAction, loginAction, logoutAction, setPasswordAction, signupAction, signupOptionsAction, startKakaoAction } from './actions';
import { ACCESS_COOKIE, KAKAO_FLOW_COOKIE, KAKAO_SIGNUP_COOKIE, REFRESH_COOKIE } from './cookies';
import { parseKakaoFlow } from './kakao';
const signupInput = { email: ' New@Devmozip.Demo ', password: 'Devmozip123', confirmPassword: 'Devmozip123', nickname: ' 새회원 ', job: '백엔드 개발자', career: '3년 차', stacks: ['Java'], interests: ['AI'], available: true };
beforeEach(() => { vi.clearAllMocks(); doubles.get.mockReturnValue({ value: 'refresh' }); });
describe('인증 서버 액션', () => {
    it('로그인은 이메일을 소문자로 맞춰 /api/v1/auth/login을 호출한다', async () => {
        doubles.fetch.mockResolvedValue(new Response('{}', { status: 401 }));
        const form = new FormData(); form.set('username', ' Member@Devmozip.Demo '); form.set('password', 'input');
        await loginAction(form);
        expect(doubles.fetch).toHaveBeenCalledWith('/api/v1/auth/login', expect.objectContaining({ body: JSON.stringify({ username: 'member@devmozip.demo', password: 'input' }) }));
    });
    it('회원가입 성공시 바로 로그인해 쿠키를 쓰고 이동은 호출한 화면에 맡긴다', async () => {
        const access = `h.${Buffer.from(JSON.stringify({ sub: 'new@devmozip.demo', role: 'USER', exp: 9999999999 })).toString('base64url')}.s`;
        doubles.fetch.mockResolvedValueOnce(Response.json({ id: 1 }, { status: 201 })).mockResolvedValueOnce(Response.json({ accessToken: access, refreshToken: 'refresh' }));
        expect(await signupAction(signupInput)).toBeNull();
        expect(doubles.fetch).toHaveBeenNthCalledWith(1, '/api/v1/auth/signup', expect.objectContaining({ body: JSON.stringify({ ...signupInput, email: 'new@devmozip.demo', nickname: '새회원' }) }));
        expect(doubles.fetch).toHaveBeenNthCalledWith(2, '/api/v1/auth/login', expect.objectContaining({ body: JSON.stringify({ username: 'new@devmozip.demo', password: 'Devmozip123' }) }));
        expect(doubles.set).toHaveBeenCalledWith(ACCESS_COOKIE, access, expect.objectContaining({ httpOnly: true }));
        expect(doubles.redirect).not.toHaveBeenCalled();
    });
    it('회원가입이 거절되면 로그인을 호출하지 않고 쿠키도 쓰지 않는다', async () => {
        doubles.fetch.mockResolvedValue(new Response('{}', { status: 409 }));
        expect(await signupAction(signupInput)).toEqual({ error: '인증 실패' });
        expect(doubles.fetch).toHaveBeenCalledTimes(1);
        expect(doubles.set).not.toHaveBeenCalled();
    });
    it('가입 후 로그인에 실패하면 다시 가입하지 않도록 로그인 화면으로 안내한다', async () => {
        doubles.fetch.mockResolvedValueOnce(Response.json({ id: 1 }, { status: 201 })).mockResolvedValueOnce(new Response('{}', { status: 503 }));
        expect(await signupAction(signupInput)).toEqual({ error: '회원가입은 완료했지만 로그인하지 못했습니다. 로그인 화면에서 다시 시도해 주세요.' });
        expect(doubles.set).not.toHaveBeenCalled();
    });
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
    it('가입 선택지는 백엔드에서 받고 실패하면 오류를 돌려준다', async () => {
        const options = { jobCategories: ['백엔드 개발자'], skills: ['Java'], interests: ['AI'] };
        doubles.fetch.mockResolvedValueOnce(Response.json(options));
        expect(await signupOptionsAction()).toEqual(options);
        expect(doubles.fetch).toHaveBeenCalledWith('/api/v1/auth/signup/options');
        doubles.fetch.mockResolvedValueOnce(new Response('{}', { status: 500 }));
        expect(await signupOptionsAction()).toHaveProperty('error');
        doubles.fetch.mockRejectedValueOnce(new Error('down'));
        expect(await signupOptionsAction()).toHaveProperty('error');
    });
    it('이메일 중복 확인은 소문자로 맞춰 백엔드에 묻는다', async () => {
        doubles.fetch.mockResolvedValue(Response.json({ available: false }));
        expect(await checkAvailabilityAction('email', ' New@Devmozip.Demo ')).toEqual({ available: false });
        expect(doubles.fetch).toHaveBeenCalledWith('/api/v1/auth/check-email?email=new%40devmozip.demo');
    });
    it('닉네임 중복 확인은 공백을 빼고 인코딩해 백엔드에 묻는다', async () => {
        doubles.fetch.mockResolvedValue(Response.json({ available: true }));
        expect(await checkAvailabilityAction('nickname', ' 새 회원 ')).toEqual({ available: true });
        expect(doubles.fetch).toHaveBeenCalledWith(`/api/v1/auth/check-nickname?nickname=${encodeURIComponent('새 회원')}`);
    });
    it('중복 확인에 실패하면 사용 가능으로 답하지 않고 오류를 돌려준다', async () => {
        doubles.fetch.mockResolvedValueOnce(new Response('{}', { status: 500 }));
        expect(await checkAvailabilityAction('email', 'a@b.co')).toEqual({ error: '인증 실패' });
        doubles.fetch.mockRejectedValueOnce(new Error('down'));
        expect(await checkAvailabilityAction('email', 'a@b.co')).toEqual({ error: '인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.' });
        doubles.fetch.mockResolvedValueOnce(Response.json({}));
        expect(await checkAvailabilityAction('email', 'a@b.co')).toEqual({ error: '중복 확인 응답을 확인할 수 없습니다.' });
    });
    it('로그아웃 성공 후 같은 옵션으로 두 쿠키를 만료시키고 이동은 호출한 화면에 맡긴다', async () => {
        doubles.fetch.mockResolvedValue(new Response(null, { status: 204 }));
        expect(await logoutAction()).toBeNull();
        expect(doubles.redirect).not.toHaveBeenCalled();
        expect(doubles.fetch).toHaveBeenCalledWith('/api/v1/auth/logout', expect.objectContaining({ body: JSON.stringify({ refreshToken: 'refresh' }) }));
        expect(doubles.set).toHaveBeenCalledWith(ACCESS_COOKIE, '', expect.objectContaining({ maxAge: 0, httpOnly: true }));
        expect(doubles.set).toHaveBeenCalledWith(REFRESH_COOKIE, '', expect.objectContaining({ maxAge: 0, httpOnly: true }));
    });
    it('Redis 장애로 로그아웃 실패시 재시도할 수 있도록 쿠키를 유지한다', async () => {
        doubles.fetch.mockResolvedValue(new Response('{}', { status: 503 }));
        expect(await logoutAction()).toEqual({ error: '인증 실패' });
        expect(doubles.set).not.toHaveBeenCalled();
    });
});
describe('카카오 서버 액션', () => {
    const access = `h.${Buffer.from(JSON.stringify({ sub: 'new@devmozip.demo', role: 'USER', exp: 9999999999 })).toString('base64url')}.s`;
    const hasSignupToken = () => doubles.get.mockImplementation((name: string) => (name === KAKAO_SIGNUP_COOKIE ? { value: 'signup-token' } : undefined));
    it('인증을 시작하면 무작위 state와 의도를 HttpOnly 쿠키에 저장하고 인가 주소를 돌려준다', async () => {
        doubles.fetch.mockResolvedValue(Response.json({ authorizeUrl: 'https://kauth.kakao.test/oauth/authorize' }));
        expect(await startKakaoAction('link')).toEqual({ url: 'https://kauth.kakao.test/oauth/authorize' });
        const [name, value, options] = doubles.set.mock.calls[0];
        expect(name).toBe(KAKAO_FLOW_COOKIE);
        expect(options).toEqual(expect.objectContaining({ httpOnly: true, sameSite: 'lax', maxAge: 600 }));
        const flow = parseKakaoFlow(value);
        expect(flow?.intent).toBe('link');
        expect(flow?.state).toMatch(/^[0-9a-f-]{36}$/);
        expect(doubles.fetch).toHaveBeenCalledWith(`/api/v1/auth/kakao?state=${flow?.state}`);
    });
    it('인증을 시작할 때마다 state가 달라진다', async () => {
        doubles.fetch.mockImplementation(async () => Response.json({ authorizeUrl: 'https://kauth.kakao.test' }));
        await startKakaoAction('login');
        await startKakaoAction('login');
        const [first, second] = doubles.set.mock.calls.map(call => parseKakaoFlow(call[1])?.state);
        expect(first).not.toBe(second);
    });
    it('이동할 경로는 내부 경로만 저장한다', async () => {
        doubles.fetch.mockResolvedValue(Response.json({ authorizeUrl: 'https://kauth.kakao.test' }));
        await startKakaoAction('login', '//outside.test');
        expect(parseKakaoFlow(doubles.set.mock.calls[0][1])?.next).toBe('/');
    });
    it('잘못된 의도나 백엔드 실패면 쿠키를 쓰지 않는다', async () => {
        expect(await startKakaoAction('admin' as never)).toHaveProperty('error');
        expect(doubles.fetch).not.toHaveBeenCalled();
        doubles.fetch.mockResolvedValueOnce(new Response('{}', { status: 503 }));
        expect(await startKakaoAction('login')).toEqual({ error: '인증 실패' });
        doubles.fetch.mockRejectedValueOnce(new Error('down'));
        expect(await startKakaoAction('login')).toHaveProperty('error');
        doubles.fetch.mockResolvedValueOnce(Response.json({}));
        expect(await startKakaoAction('login')).toHaveProperty('error');
        expect(doubles.set).not.toHaveBeenCalled();
    });
    it('카카오 가입은 비밀번호 없이 가입 토큰을 붙여 보내고 응답의 토큰으로 로그인한다', async () => {
        hasSignupToken();
        doubles.fetch.mockResolvedValue(Response.json({ id: 1, accessToken: access, refreshToken: 'refresh', tokenType: 'Bearer' }, { status: 201 }));
        expect(await signupAction(signupInput, { kakao: true })).toBeNull();
        expect(doubles.fetch).toHaveBeenCalledTimes(1);
        const [path, init] = doubles.fetch.mock.calls[0];
        expect(path).toBe('/api/v1/auth/signup');
        expect(JSON.parse(init.body)).toEqual({ email: 'new@devmozip.demo', nickname: '새회원', job: '백엔드 개발자', career: '3년 차', stacks: ['Java'], interests: ['AI'], available: true, kakaoSignupToken: 'signup-token' });
        expect(doubles.set).toHaveBeenCalledWith(ACCESS_COOKIE, access, expect.objectContaining({ httpOnly: true }));
        expect(doubles.set).toHaveBeenCalledWith(REFRESH_COOKIE, 'refresh', expect.objectContaining({ httpOnly: true }));
        expect(doubles.set).toHaveBeenCalledWith(KAKAO_SIGNUP_COOKIE, '', expect.objectContaining({ maxAge: 0 }));
    });
    it('가입 토큰이 없으면 백엔드를 호출하지 않고 다시 인증하도록 안내한다', async () => {
        doubles.get.mockReturnValue(undefined);
        expect(await signupAction(signupInput, { kakao: true })).toEqual({ error: '카카오 인증이 만료되었습니다. 다시 시도해 주세요.' });
        expect(doubles.fetch).not.toHaveBeenCalled();
    });
    it('카카오 가입이 거절되면 입력을 고쳐 다시 보낼 수 있도록 가입 토큰을 유지한다', async () => {
        hasSignupToken();
        doubles.fetch.mockResolvedValue(new Response('{}', { status: 409 }));
        expect(await signupAction(signupInput, { kakao: true })).toEqual({ error: '인증 실패' });
        expect(doubles.set).not.toHaveBeenCalled();
    });
    it('이메일 가입은 남아 있는 가입 토큰을 보내지 않는다', async () => {
        hasSignupToken();
        doubles.fetch.mockResolvedValue(new Response('{}', { status: 409 }));
        await signupAction(signupInput);
        expect(doubles.fetch.mock.calls[0][1].body).not.toContain('signup-token');
    });
    it('연동 상태를 백엔드에서 받고 실패하면 오류를 돌려준다', async () => {
        doubles.fetch.mockResolvedValueOnce(Response.json({ kakaoLinked: true, passwordSet: false, extra: 'x' }));
        expect(await kakaoAccountStatusAction()).toEqual({ kakaoLinked: true, passwordSet: false });
        expect(doubles.fetch).toHaveBeenCalledWith('/api/v1/users/me/social-accounts');
        doubles.fetch.mockResolvedValueOnce(new Response('{}', { status: 401 }));
        expect(await kakaoAccountStatusAction()).toHaveProperty('error');
        doubles.fetch.mockResolvedValueOnce(Response.json({ kakaoLinked: 'yes' }));
        expect(await kakaoAccountStatusAction()).toHaveProperty('error');
        doubles.fetch.mockRejectedValueOnce(new Error('down'));
        expect(await kakaoAccountStatusAction()).toHaveProperty('error');
    });
    it('비밀번호 설정은 새 비밀번호만 전달하고 실패 문구를 돌려준다', async () => {
        doubles.fetch.mockResolvedValueOnce(new Response(null, { status: 204 }));
        expect(await setPasswordAction({ newPassword: 'Devmozip123', confirmPassword: 'Devmozip123', currentPassword: 'x' } as never)).toBeNull();
        expect(doubles.fetch).toHaveBeenCalledWith('/api/v1/users/me/password', expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ newPassword: 'Devmozip123', confirmPassword: 'Devmozip123' }) }));
        doubles.fetch.mockResolvedValueOnce(new Response('{}', { status: 409 }));
        expect(await setPasswordAction({ newPassword: 'a', confirmPassword: 'a' })).toEqual({ error: '인증 실패' });
        doubles.fetch.mockRejectedValueOnce(new Error('down'));
        expect(await setPasswordAction({ newPassword: 'a', confirmPassword: 'a' })).toHaveProperty('error');
    });
});
