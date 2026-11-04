'use server';
import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import { normalizeEmail } from '@/lib/account/authService';
import type { SignupInput } from '@/types/auth';
import { backendFetch, readErrorMessage } from './backend';
import { ACCESS_COOKIE, REFRESH_COOKIE, cookieOptions, REFRESH_MAX_AGE } from './cookies';
import { decodeToken } from './token';
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
    const data = await response.json();
    const payload = decodeToken(data.accessToken);
    if (!payload?.role || typeof data.refreshToken !== 'string') return { error: '인증 응답을 확인할 수 없습니다.' };
    const jar = await cookies();
    jar.set(ACCESS_COOKIE, data.accessToken, { ...cookieOptions, maxAge: Math.max(0, payload.exp - Math.floor(Date.now() / 1000)) });
    jar.set(REFRESH_COOKIE, data.refreshToken, { ...cookieOptions, maxAge: REFRESH_MAX_AGE });
    return null;
}

export async function loginAction(form: FormData): Promise<{ error: string } | never> {
    const failure = await startSession(normalizeEmail(String(form.get('username') ?? '')), String(form.get('password') ?? ''));
    if (failure) return failure;
    redirect(safeNext(form.get('next')));
}

// 가입 직후 바로 로그인한다. 화면 이동은 호출한 쪽(가입 폼)이 맡는다.
export async function signupAction(input: SignupInput): Promise<{ error: string } | null> {
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
    jar.set(ACCESS_COOKIE, '', { ...cookieOptions, maxAge: 0 });
    jar.set(REFRESH_COOKIE, '', { ...cookieOptions, maxAge: 0 });
    return null;
}
