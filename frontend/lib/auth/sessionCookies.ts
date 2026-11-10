import 'server-only';
import { cookies } from 'next/headers';
import { ACCESS_COOKIE, REFRESH_COOKIE, REFRESH_MAX_AGE, cookieOptions } from './cookies';
import { decodeToken } from './token';

// 백엔드가 준 토큰으로 로그인 쿠키를 쓴다. 토큰을 확인할 수 없으면 쿠키를 쓰지 않고 false를 돌려준다.
export async function writeSessionCookies({ accessToken, refreshToken }: { accessToken?: unknown; refreshToken?: unknown }) {
    if (typeof accessToken !== 'string' || typeof refreshToken !== 'string') return false;
    const payload = decodeToken(accessToken);
    if (!payload?.role) return false;
    const jar = await cookies();
    jar.set(ACCESS_COOKIE, accessToken, { ...cookieOptions, maxAge: Math.max(0, payload.exp - Math.floor(Date.now() / 1000)) });
    jar.set(REFRESH_COOKIE, refreshToken, { ...cookieOptions, maxAge: REFRESH_MAX_AGE });
    return true;
}
