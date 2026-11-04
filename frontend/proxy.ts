import { NextResponse, type NextRequest } from 'next/server';
import { ACCESS_COOKIE, REFRESH_COOKIE, cookieOptions } from '@/lib/auth/cookies';
import { decodeToken, isExpired } from '@/lib/auth/token';
import { refreshAccessToken } from '@/lib/auth/refresh';
import { hasPrefix, isProtectedPath } from '@/lib/auth/paths';

export async function proxy(request: NextRequest) {
    // 헬스체크에는 쿠키와 Redis 재발급 요청이 끼어들지 않는다.
    if (request.nextUrl.pathname === '/api/health') return NextResponse.next();
    let access = request.cookies.get(ACCESS_COOKIE)?.value;
    let payload = decodeToken(access);
    let refreshed = false;
    let cleared = false;
    if (isExpired(payload) || !payload?.role) {
        const refresh = request.cookies.get(REFRESH_COOKIE)?.value;
        access = refresh ? await refreshAccessToken(refresh) ?? undefined : undefined;
        payload = decodeToken(access);
        if (access && payload?.role && !isExpired(payload)) {
            request.cookies.set(ACCESS_COOKIE, access);
            refreshed = true;
        } else {
            request.cookies.delete(ACCESS_COOKIE);
            request.cookies.delete(REFRESH_COOKIE);
            cleared = true;
        }
    }
    const loggedIn = !!payload?.role && !isExpired(payload) && !cleared;
    const pathname = request.nextUrl.pathname;
    let response: NextResponse;
    if (isProtectedPath(pathname) && !loggedIn) {
        const target = new URL('/login', request.url);
        target.searchParams.set('next', pathname + request.nextUrl.search);
        response = NextResponse.redirect(target);
    } else if (hasPrefix(pathname, '/admin') && payload?.role !== 'ADMIN') {
        response = NextResponse.redirect(new URL('/', request.url));
    } else {
        const headers = new Headers(request.headers);
        headers.set('cookie', request.cookies.toString());
        response = NextResponse.next({ request: { headers } });
    }
    if (refreshed && access && payload) response.cookies.set(ACCESS_COOKIE, access, { ...cookieOptions, maxAge: Math.max(0, payload.exp - Math.floor(Date.now() / 1000)) });
    if (cleared) {
        response.cookies.set(ACCESS_COOKIE, '', { ...cookieOptions, maxAge: 0 });
        response.cookies.set(REFRESH_COOKIE, '', { ...cookieOptions, maxAge: 0 });
    }
    return response;
}
export const config = { matcher: ['/((?!_next/static|_next/image|favicon.ico|.*\\..*).*)'] };
