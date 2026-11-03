'use server';
import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import { backendFetch, readErrorMessage } from './backend';
import { ACCESS_COOKIE, REFRESH_COOKIE, cookieOptions, REFRESH_MAX_AGE } from './cookies';
import { decodeToken } from './token';
import { safeNext } from './paths';
import { authMode } from './env';

export async function loginAction(form: FormData): Promise<{ error: string } | never> {
    if (authMode() !== 'server') return { error: '서버 인증 모드에서 이용해 주세요.' };
    let response: Response;
    try {
        response = await backendFetch('/api/auth/login', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: String(form.get('username') ?? '').trim(), password: String(form.get('password') ?? '') }),
        });
    } catch { return { error: '인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.' }; }
    if (!response.ok) return { error: await readErrorMessage(response) };
    const data = await response.json();
    const payload = decodeToken(data.accessToken);
    if (!payload?.role || typeof data.refreshToken !== 'string') return { error: '인증 응답을 확인할 수 없습니다.' };
    const jar = await cookies();
    jar.set(ACCESS_COOKIE, data.accessToken, { ...cookieOptions, maxAge: Math.max(0, payload.exp - Math.floor(Date.now() / 1000)) });
    jar.set(REFRESH_COOKIE, data.refreshToken, { ...cookieOptions, maxAge: REFRESH_MAX_AGE });
    redirect(safeNext(form.get('next')));
}

export async function logoutAction(): Promise<{ error: string } | never> {
    const jar = await cookies();
    const refreshToken = jar.get(REFRESH_COOKIE)?.value;
    if (refreshToken) {
        try {
            const response = await backendFetch('/api/auth/logout', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken }),
            });
            if (!response.ok) return { error: await readErrorMessage(response) };
        } catch { return { error: '로그아웃을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.' }; }
    }
    jar.set(ACCESS_COOKIE, '', { ...cookieOptions, maxAge: 0 });
    jar.set(REFRESH_COOKIE, '', { ...cookieOptions, maxAge: 0 });
    redirect('/');
}
