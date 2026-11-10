import { cookies } from 'next/headers';
import { backendFetch } from '@/lib/auth/backend';
import { KAKAO_FLOW_COOKIE, KAKAO_SIGNUP_COOKIE, KAKAO_SIGNUP_MAX_AGE, cookieOptions } from '@/lib/auth/cookies';
import { kakaoResultPath, parseKakaoFlow, type KakaoFlow } from '@/lib/auth/kakao';
import { loginNext } from '@/lib/auth/paths';
import { writeSessionCookies } from '@/lib/auth/sessionCookies';

// 백엔드가 카카오를 두 번 호출하므로 기본 5초보다 길게 기다린다.
const KAKAO_TIMEOUT = 20_000;
const LOGIN_AGAIN = `/login?next=${encodeURIComponent('/settings/privacy')}`;

// 컨테이너 내부 주소가 섞이지 않도록 상대 경로로 이동한다.
const redirectTo = (path: string) => new Response(null, { status: 302, headers: { Location: path, 'Cache-Control': 'no-store' } });

function failurePath(flow: KakaoFlow, status: number) {
    if (status === 403) return kakaoResultPath(flow.intent === 'signup' ? 'signup' : 'login', 'suspended');
    return kakaoResultPath(flow.intent, status === 503 ? 'unavailable' : 'failed');
}

async function completeLogin(flow: KakaoFlow, code: string) {
    const response = await backendFetch(`/api/v1/auth/kakao/callback?code=${encodeURIComponent(code)}&intent=${flow.intent}`, { signal: AbortSignal.timeout(KAKAO_TIMEOUT) });
    if (!response.ok) return failurePath(flow, response.status);
    const data = await response.json();
    if (data.status === 'LOGIN') return await writeSessionCookies(data) ? loginNext(flow.next) : kakaoResultPath(flow.intent, 'failed');
    if (data.status === 'ALREADY_REGISTERED') return kakaoResultPath(flow.intent, 'already-registered');
    if (data.status !== 'SIGNUP_REQUIRED' || typeof data.kakaoSignupToken !== 'string') return kakaoResultPath(flow.intent, 'failed');
    (await cookies()).set(KAKAO_SIGNUP_COOKIE, data.kakaoSignupToken, { ...cookieOptions, maxAge: KAKAO_SIGNUP_MAX_AGE });
    return kakaoResultPath('signup', 'signup');
}

async function completeLink(flow: KakaoFlow, code: string) {
    const response = await backendFetch('/api/v1/users/me/social-accounts/kakao', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ code }), signal: AbortSignal.timeout(KAKAO_TIMEOUT),
    });
    if (response.ok) return kakaoResultPath('link', 'linked');
    if (response.status === 401) return LOGIN_AGAIN;
    return response.status === 409 ? kakaoResultPath('link', 'conflict') : failurePath(flow, response.status);
}

async function completeUnlink(flow: KakaoFlow, code: string) {
    const response = await backendFetch(`/api/v1/users/me/social-accounts/kakao?code=${encodeURIComponent(code)}`, { method: 'DELETE', signal: AbortSignal.timeout(KAKAO_TIMEOUT) });
    if (response.ok) return kakaoResultPath('unlink', 'unlinked');
    if (response.status === 401) return LOGIN_AGAIN;
    return response.status === 409 ? kakaoResultPath('unlink', 'unlink-blocked') : failurePath(flow, response.status);
}

// 카카오 콘솔에 등록한 Redirect URI. 브라우저 쿠키의 state를 대조한 뒤 처음 시작한 의도대로 백엔드를 호출한다.
export async function GET(request: Request) {
    const jar = await cookies();
    const flow = parseKakaoFlow(jar.get(KAKAO_FLOW_COOKIE)?.value);
    jar.set(KAKAO_FLOW_COOKIE, '', { ...cookieOptions, maxAge: 0 });

    const params = new URL(request.url).searchParams;
    if (!flow || params.get('state') !== flow.state) return redirectTo(kakaoResultPath(flow?.intent ?? 'login', 'invalid-state'));

    const error = params.get('error');
    if (error) return redirectTo(kakaoResultPath(flow.intent, error === 'access_denied' ? 'cancelled' : 'failed'));
    const code = params.get('code');
    if (!code) return redirectTo(kakaoResultPath(flow.intent, 'failed'));

    // 이전에 끝내지 못한 가입이 남아 있어도 이번 결과만 쓰도록 비운다.
    jar.set(KAKAO_SIGNUP_COOKIE, '', { ...cookieOptions, maxAge: 0 });
    try {
        if (flow.intent === 'link') return redirectTo(await completeLink(flow, code));
        if (flow.intent === 'unlink') return redirectTo(await completeUnlink(flow, code));
        return redirectTo(await completeLogin(flow, code));
    } catch {
        return redirectTo(kakaoResultPath(flow.intent, 'failed'));
    }
}
