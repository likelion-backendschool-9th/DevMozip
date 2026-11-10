import 'server-only';
import { cookies } from 'next/headers';
import { ACCESS_COOKIE } from './cookies';
import { decodeToken, isExpired } from './token';
import { backendFetch } from './backend';
import type { ServerIdentity } from '@/types/session';

export async function getSession() {
    const token = (await cookies()).get(ACCESS_COOKIE)?.value;
    const payload = decodeToken(token);
    if (!token || !payload?.role || isExpired(payload)) return null;
    // 쿠키를 조작해 만든 role을 서버 세션으로 받아들이지 않는다.
    const response = await backendFetch('/api/v1/users/me');
    if (!response.ok) return null;
    const identity: ServerIdentity = await response.json();
    return { ...identity, isAdmin: identity.role === 'ADMIN', token };
}
