'use server';
import { cookies, headers } from 'next/headers';
import { normalizeEmail } from '@/lib/account/authService';
import type { PasswordChangeInput } from '@/types/account';
import type { KakaoAccountStatus, KakaoIntent, PasswordResetInput, PasswordSetInput, SignupInput, SignupOptions } from '@/types/auth';
import { backendFetch, readErrorMessage } from './backend';
import { ACCESS_COOKIE, REFRESH_COOKIE, KAKAO_FLOW_COOKIE, KAKAO_FLOW_MAX_AGE, KAKAO_SIGNUP_COOKIE, WITHDRAWAL_KAKAO_COOKIE, cookieOptions } from './cookies';
import { isKakaoIntent, serializeKakaoFlow } from './kakao';
import { writeSessionCookies } from './sessionCookies';
import { safeNext } from './paths';

// 백엔드 로그인을 호출하고 두 쿠키를 쓴다. 화면 이동은 호출한 쪽이 맡는다.
async function startSession(username: string, password: string): Promise<{ error: string } | null> {
    let response: Response;
    try {
        response = await backendFetch('/api/v1/auth/login', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password }),
        });
    } catch { return { error: '인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.' }; }
    if (!response.ok) return { error: await readErrorMessage(response) };
    return await writeSessionCookies(await response.json()) ? null : { error: '인증 응답을 확인할 수 없습니다.' };
}

export async function loginAction(
    form: FormData,
): Promise<{ error: string } | { redirectTo: string }> {
    const failure = await startSession(
        normalizeEmail(String(form.get('username') ?? '')),
        String(form.get('password') ?? ''),
    );
    if (failure) return failure;

    const target = safeNext(form.get('next'));
    return {
        redirectTo:
            new URL(target, 'https://internal.invalid').pathname === '/login'
                ? '/'
                : target,
    };
}

// 가입 화면의 직군·기술 스택·관심 분야 선택지. 기준은 백엔드 enum이다.
export async function signupOptionsAction(): Promise<SignupOptions | { error: string }> {
    const failure = { error: '선택 항목을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.' };
    try {
        const response = await backendFetch('/api/v1/auth/signup/options');
        return response.ok ? await response.json() : failure;
    } catch { return failure; }
}

// 카카오 가입: 가입 토큰을 붙여 비밀번호 없이 가입하고, 응답의 토큰으로 바로 로그인한다.
async function kakaoSignup(input: SignupInput): Promise<{ error: string } | null> {
    const jar = await cookies();
    const kakaoSignupToken = jar.get(KAKAO_SIGNUP_COOKIE)?.value;
    if (!kakaoSignupToken) return { error: '카카오 인증이 만료되었습니다. 다시 시도해 주세요.' };
    const { job, career, skills, interests, available } = input;
    let response: Response;
    try {
        response = await backendFetch('/api/v1/auth/signup', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: normalizeEmail(input.email), nickname: input.nickname.trim(), job, career, skills, interests, available, kakaoSignupToken }),
        });
    } catch { return { error: '인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.' }; }
    if (!response.ok) return { error: await readErrorMessage(response) };
    jar.set(KAKAO_SIGNUP_COOKIE, '', { ...cookieOptions, maxAge: 0 });
    return await writeSessionCookies(await response.json()) ? null : { error: '회원가입은 완료했지만 로그인하지 못했습니다. 로그인 화면에서 다시 시도해 주세요.' };
}

// 가입 직후 바로 로그인한다. 화면 이동은 호출한 쪽(가입 폼)이 맡는다.
export async function signupAction(input: SignupInput, options?: { kakao?: boolean }): Promise<{ error: string } | null> {
    if (options?.kakao) return kakaoSignup(input);
    const email = normalizeEmail(input.email);
    let response: Response;
    try {
        response = await backendFetch('/api/v1/auth/signup', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ ...input, email, nickname: input.nickname.trim() }),
        });
    } catch { return { error: '인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.' }; }
    if (!response.ok) return { error: await readErrorMessage(response) };
    // 여기서 실패해도 계정은 이미 만들어졌으므로, 다시 가입하지 않도록 로그인 화면으로 안내한다.
    const failure = await startSession(email, input.password);
    return failure ? { error: '회원가입은 완료했지만 로그인하지 못했습니다. 로그인 화면에서 다시 시도해 주세요.' } : null;
}

// 가입 화면의 중복 확인 버튼. 브라우저 목록이 아니라 실제 DB를 기준으로 답한다.
export async function checkAvailabilityAction(field: 'email' | 'nickname', value: string): Promise<{ available: boolean } | { error: string }> {
    const path = field === 'email' ? `/api/v1/auth/check-email?email=${encodeURIComponent(normalizeEmail(value))}` : `/api/v1/auth/check-nickname?nickname=${encodeURIComponent(value.trim())}`;
    let response: Response;
    try {
        response = await backendFetch(path);
    } catch { return { error: '인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.' }; }
    if (!response.ok) return { error: await readErrorMessage(response) };
    const data = await response.json();
    return typeof data.available === 'boolean' ? { available: data.available } : { error: '중복 확인 응답을 확인할 수 없습니다.' };
}

// 서버 액션(POST)으로 시작해 외부 페이지가 링크만으로 연동·해제를 유도할 수 없게 한다. 카카오로 이동은 호출한 화면이 맡는다.
export async function startKakaoAction(intent: KakaoIntent, next?: string): Promise<{ url: string } | { error: string }> {
    if (!isKakaoIntent(intent)) return { error: '잘못된 요청입니다.' };
    if (intent === 'withdraw') {
        try {
            if (!(await backendFetch('/api/v1/users/me')).ok) return { error: '로그인 후 다시 시도해 주세요.' };
        } catch { return { error: '계정 정보를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.' }; }
        (await cookies()).set(WITHDRAWAL_KAKAO_COOKIE, '', { ...cookieOptions, maxAge: 0 });
    }
    const state = crypto.randomUUID();
    const failure = { error: '카카오 인증을 시작하지 못했습니다. 잠시 후 다시 시도해 주세요.' };
    let response: Response;
    try {
        response = await backendFetch(`/api/v1/auth/kakao?state=${encodeURIComponent(state)}`);
    } catch { return failure; }
    if (!response.ok) return { error: await readErrorMessage(response) };
    const data = await response.json();
    if (typeof data.authorizeUrl !== 'string') return failure;
    if (intent === 'withdraw') {
        try {
            const url = new URL(data.authorizeUrl);
            if (url.protocol !== 'https:' || url.hostname !== 'kauth.kakao.com') return failure;
            url.searchParams.set('prompt', 'login');
            data.authorizeUrl = url.toString();
        } catch { return failure; }
    }
    (await cookies()).set(KAKAO_FLOW_COOKIE, serializeKakaoFlow({ state, intent, next: safeNext(next) }), { ...cookieOptions, maxAge: KAKAO_FLOW_MAX_AGE });
    return { url: data.authorizeUrl };
}

// 개인정보 설정 화면의 카카오 연동 상태와 비밀번호 설정 여부
export async function kakaoAccountStatusAction(): Promise<KakaoAccountStatus | { error: string }> {
    const failure = { error: '계정 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.' };
    try {
        const response = await backendFetch('/api/v1/users/me/social-accounts');
        if (!response.ok) return failure;
        const { kakaoLinked, passwordSet } = await response.json();
        return typeof kakaoLinked === 'boolean' && typeof passwordSet === 'boolean' ? { kakaoLinked, passwordSet } : failure;
    } catch { return failure; }
}

// 비밀번호 없는 카카오 회원의 첫 비밀번호 설정
export async function setPasswordAction({ newPassword, confirmPassword }: PasswordSetInput): Promise<{ error: string } | null> {
    let response: Response;
    try {
        response = await backendFetch('/api/v1/users/me/password', {
            method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ newPassword, confirmPassword }),
        });
    } catch { return { error: '인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.' }; }
    return response.ok ? null : { error: await readErrorMessage(response) };
}

async function clearSessionCookies() {
    const jar = await cookies();
    jar.set(ACCESS_COOKIE, '', { ...cookieOptions, maxAge: 0 });
    jar.set(REFRESH_COOKIE, '', { ...cookieOptions, maxAge: 0 });
}

// 로그인한 회원의 비밀번호 변경. 성공하면 모든 기기에서 로그아웃되므로 쿠키를 지운다. 화면 이동은 호출한 쪽이 맡는다.
export async function changePasswordAction({ currentPassword, newPassword, confirmPassword }: PasswordChangeInput): Promise<{ error: string } | null> {
    let response: Response;
    try {
        response = await backendFetch('/api/v1/users/me/password', {
            method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ currentPassword, newPassword, confirmPassword }),
        });
    } catch { return { error: '인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.' }; }
    if (!response.ok) return { error: await readErrorMessage(response) };
    await clearSessionCookies();
    return null;
}

// 재설정 메일 요청. 백엔드의 IP 제한에 쓰도록 사용자 IP를 함께 넘긴다.
export async function requestPasswordResetAction(email: string): Promise<{ error: string } | null> {
    const clientIp = (await headers()).get('x-real-ip');
    let response: Response;
    try {
        response = await backendFetch('/api/v1/auth/password/reset-request', {
            method: 'POST', headers: { 'Content-Type': 'application/json', ...(clientIp ? { 'X-Real-IP': clientIp } : {}) },
            body: JSON.stringify({ email: normalizeEmail(email) }),
        });
    } catch { return { error: '인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.' }; }
    return response.ok ? null : { error: await readErrorMessage(response) };
}

// 메일 링크로 새 비밀번호를 저장한다. 화면 이동은 호출한 쪽이 맡는다. 401이면 링크가 쓸 수 없는 상태이므로 expired로 알린다.
export async function resetPasswordAction({ token, newPassword, confirmPassword }: PasswordResetInput): Promise<{ error: string; expired?: boolean } | null> {
    let response: Response;
    try {
        response = await backendFetch('/api/v1/auth/password/reset', {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token, newPassword, confirmPassword }),
        });
    } catch { return { error: '인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.' }; }
    if (response.status === 401) return { error: await readErrorMessage(response), expired: true };
    if (!response.ok) return { error: await readErrorMessage(response) };
    // 이 브라우저에 다른 계정으로 로그인되어 있어도 새 비밀번호로 다시 로그인하게 한다.
    await clearSessionCookies();
    return null;
}

// 화면 이동은 호출한 쪽(헤더 로그아웃 버튼)이 맡는다. 여기서 redirect하면 클라이언트 이동과 겹쳐 결과를 받지 못한다.
export async function logoutAction(): Promise<{ error: string } | null> {
    const jar = await cookies();
    const refreshToken = jar.get(REFRESH_COOKIE)?.value;
    if (refreshToken) {
        try {
            const response = await backendFetch('/api/v1/auth/logout', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken }),
            });
            if (!response.ok) return { error: await readErrorMessage(response) };
        } catch { return { error: '로그아웃을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.' }; }
    }
    await clearSessionCookies();
    return null;
}

// 비밀번호는 서버 요청 본문으로만 전달한다. 카카오 인가 코드는 HttpOnly 쿠키에서만 읽는다.
export async function withdrawAccountAction(input: { currentPassword: string; confirmed: boolean }): Promise<{ error: string } | null> {
    if (input.confirmed !== true) return { error: '개인정보 삭제와 환불 불가 안내를 확인해 주세요.' };
    const jar = await cookies();
    let kakaoCode: string | undefined;
    try {
        const saved = JSON.parse(jar.get(WITHDRAWAL_KAKAO_COOKIE)?.value ?? 'null');
        if (saved && saved.session === jar.get(ACCESS_COOKIE)?.value && typeof saved.code === 'string') kakaoCode = saved.code;
    } catch { return { error: '카카오 인증 정보를 확인하지 못했습니다. 다시 인증해 주세요.' }; }
    let response: Response;
    try {
        response = await backendFetch('/api/v1/users/me', {
            method: 'DELETE', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ currentPassword: input.currentPassword, confirmed: true, kakaoCode }),
            signal: AbortSignal.timeout(60_000),
        });
    } catch { return { error: '탈퇴 결과를 확인하지 못했습니다. 로그인 상태를 확인한 후 다시 시도해 주세요.' }; }
    jar.set(WITHDRAWAL_KAKAO_COOKIE, '', { ...cookieOptions, maxAge: 0 });
    if (!response.ok) return { error: await readErrorMessage(response) };
    await clearSessionCookies();
    jar.set(KAKAO_FLOW_COOKIE, '', { ...cookieOptions, maxAge: 0 });
    jar.set(KAKAO_SIGNUP_COOKIE, '', { ...cookieOptions, maxAge: 0 });
    return null;
}
