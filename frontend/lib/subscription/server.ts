import 'server-only';
import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { backendFetch } from '@/lib/auth/backend';
import { refreshAccessToken } from '@/lib/auth/refresh';
import { ACCESS_COOKIE, REFRESH_COOKIE, cookieOptions } from '@/lib/auth/cookies';
import { decodeToken } from '@/lib/auth/token';
import { failureCodeForStatus } from './subscriptionView';
import type { CheckoutFailureCode } from '@/types/subscription';

// 구독·결제 BFF 공통. 사용자 ID는 백엔드로 보내지 않고, 백엔드는 쿠키의 액세스 토큰으로 요청자를 정한다.

/** 승인은 포트원 빌링키 조회와 결제를 거치므로 기본 5초보다 길게 기다린다(백엔드의 포트원 읽기 제한 20초 × 2) */
export const CONFIRM_TIMEOUT = 45_000;
/** 해지는 커밋 후 포트원 빌링키 삭제까지 기다린다 */
export const CANCEL_TIMEOUT = 25_000;

const NO_STORE = { 'Cache-Control': 'no-store' };
const BACKEND_ERROR = '서버 응답을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.';

export function jsonError(status: number, message: string) {
    return NextResponse.json({ message }, { status, headers: NO_STORE });
}

/**
 * 같은 사이트의 JSON 요청만 받는다. 통과하면 null.
 * standalone 서버의 request.url은 Host와 관계없이 localhost:포트로 만들어지므로, 브라우저가 보낸 Origin의 호스트를
 * nginx가 넘겨 준 X-Forwarded-Host(없으면 Host)와 비교한다(Next 서버 액션의 확인 방식과 같다).
 */
export function rejectCrossSite(request: Request) {
    if (!isSameOrigin(request)) return jsonError(403, '같은 사이트에서 요청해 주세요.');
    if (!request.headers.get('content-type')?.startsWith('application/json')) return jsonError(415, 'JSON 형식이 필요합니다.');
    return null;
}

export function isSameOrigin(request: Request) {
    const origin = request.headers.get('origin');
    const host = (request.headers.get('x-forwarded-host') ?? request.headers.get('host'))?.split(',')[0].trim().toLowerCase();
    if (!origin || !host) return false;
    try {
        return new URL(origin).host.toLowerCase() === host;
    } catch {
        return false;
    }
}

/** 백엔드 응답을 상태 코드와 본문 그대로 전달한다 */
export async function forward(path: string, init: RequestInit = {}) {
    try {
        const response = await backendFetch(path, init);
        if (response.status === 204) return new NextResponse(null, { status: 204, headers: NO_STORE });
        const body = await response.json().catch(() => null);
        if (body === null) return jsonError(response.ok ? 502 : response.status, BACKEND_ERROR);
        return NextResponse.json(body, { status: response.status, headers: NO_STORE });
    } catch {
        return jsonError(502, BACKEND_ERROR);
    }
}

export type ConfirmResult = { ok: true } | { ok: false; status: number; code: CheckoutFailureCode; message: string };

/**
 * 결제 승인. 성공하면 리프레시 토큰으로 액세스 토큰을 다시 받아 쿠키를 바꾼다.
 * 결제 직후의 액세스 토큰에는 이전 역할(USER)이 들어 있기 때문이다. 재발급은 DB의 역할(PREMIUM)을 읽는다.
 */
export async function confirmPayment(orderId: string, billingKey: string): Promise<ConfirmResult> {
    let response: Response;
    try {
        response = await backendFetch('/api/v1/payments/confirm', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ orderId, billingKey }), signal: AbortSignal.timeout(CONFIRM_TIMEOUT),
        });
    } catch {
        // 응답을 받지 못했다. 결제가 됐을 수도 있으므로 결제 내역 확인을 안내한다.
        return { ok: false, status: 502, code: 'unknown', message: '결제 결과를 확인하지 못했습니다.' };
    }
    if (!response.ok) {
        const body = await response.json().catch(() => null);
        return { ok: false, status: response.status, code: failureCodeForStatus(response.status), message: typeof body?.message === 'string' ? body.message : BACKEND_ERROR };
    }
    await refreshSessionRole();
    return { ok: true };
}

/** 리프레시 토큰으로 새 액세스 토큰을 받아 쿠키에 쓴다. 실패해도 결제 결과는 바뀌지 않으며 다음 재발급 때 반영된다 */
export async function refreshSessionRole() {
    const jar = await cookies();
    const refreshToken = jar.get(REFRESH_COOKIE)?.value;
    if (!refreshToken) return false;
    const accessToken = await refreshAccessToken(refreshToken);
    const payload = decodeToken(accessToken ?? undefined);
    if (!accessToken || !payload?.role) return false;
    jar.set(ACCESS_COOKIE, accessToken, { ...cookieOptions, maxAge: Math.max(0, payload.exp - Math.floor(Date.now() / 1000)) });
    return true;
}

export function isOrderId(value: unknown): value is string {
    return typeof value === 'string' && value.length > 0 && value.length <= 64 && /^[A-Za-z0-9-]+$/.test(value);
}

export function isBillingKey(value: unknown): value is string {
    return typeof value === 'string' && value.length > 0 && value.length <= 200 && !/[\s<>"'\\]/.test(value);
}
