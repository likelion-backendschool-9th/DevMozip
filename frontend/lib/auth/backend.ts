import 'server-only';
import { cookies } from 'next/headers';
import { BACKEND_URL } from './env';
import { ACCESS_COOKIE } from './cookies';

export async function backendFetch(path: string, init: RequestInit = {}) {
    if (!path.startsWith('/') || path.startsWith('//')) throw new Error('내부 API 경로가 필요합니다.');
    const headers = new Headers(init.headers);
    const token = (await cookies()).get(ACCESS_COOKIE)?.value;
    if (token) headers.set('Authorization', `Bearer ${token}`);
    return fetch(`${BACKEND_URL}${path}`, { ...init, headers, cache: 'no-store', signal: init.signal ?? AbortSignal.timeout(5000) });
}
export async function readErrorMessage(response: Response) {
    try {
        const body = await response.json();
        if (typeof body.message === 'string') return body.message;
    } catch { /* 응답 본문이 없어도 HTTP 상태를 안내한다. */ }
    return `요청이 실패했어요. (HTTP ${response.status})`;
}
