import 'server-only';
import { NextResponse } from 'next/server';
import { backendFetch } from '@/lib/auth/backend';
import { getSession } from '@/lib/auth/session';

// 관리자 BFF 공통 (docs/admin-plan.md 6.1). 요청자 확인은 백엔드가 다시 하므로 여기서는 먼저 막는 역할만 한다.
const headers = { 'Cache-Control': 'no-store' };

// 401·403은 고정 문구를 쓴다. 400·404·409는 백엔드가 정한 안내(사유 누락, 이미 처리된 신고 등)를 그대로 보여 준다.
const fixedMessages: Record<number, string> = {
    401: '로그인이 필요해요. 다시 로그인해 주세요.',
    403: '관리자만 이용할 수 있어요.',
};
const fallbackMessages: Record<number, string> = {
    400: '요청 내용을 확인해 주세요.',
    404: '대상을 찾을 수 없어요. 목록을 새로 고쳐 주세요.',
    409: '현재 상태에서는 처리할 수 없어요. 목록을 새로 고쳐 주세요.',
};

export const adminError = (status: number, message: string) => NextResponse.json({ message }, { status, headers });

/**
 * 쓰기 요청은 같은 사이트에서 왔는지 확인한다(알림·프로젝트 BFF와 같은 방식).
 * 모든 요청은 서버 세션의 역할이 ADMIN인지 확인한다. 세션 역할은 백엔드 /api/v1/users/me가 확인한 값이다.
 */
export async function adminGuard(request: Request, write = false) {
    if (write) {
        const url = new URL(request.url);
        let origin: URL;
        try { origin = new URL(request.headers.get('origin') ?? ''); }
        catch { return adminError(403, '같은 사이트에서 요청해 주세요.'); }
        if (origin.host !== (request.headers.get('host') ?? url.host) || origin.protocol !== url.protocol)
            return adminError(403, '같은 사이트에서 요청해 주세요.');
    }
    try {
        const session = await getSession();
        if (!session) return adminError(401, fixedMessages[401]);
        if (session.role !== 'ADMIN') return adminError(403, fixedMessages[403]);
    } catch {
        return adminError(502, '인증 상태를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.');
    }
    return null;
}

async function backendMessage(response: Response) {
    try {
        const body: unknown = await response.json();
        const message = body && typeof body === 'object' ? (body as Record<string, unknown>).message : undefined;
        return typeof message === 'string' && message.trim() ? message : null;
    } catch { return null; }
}

/**
 * 백엔드를 부르고 응답 형식을 확인한 값만 넘긴다. parse가 예외를 던지면 502다.
 * 204는 본문 없이 넘긴다. 백엔드 5xx와 연결 오류는 내부 내용을 숨긴다.
 */
export async function proxyAdmin<T>(path: string, init: RequestInit, parse: ((body: unknown) => T) | null) {
    let response: Response;
    try { response = await backendFetch(path, init); }
    catch { return adminError(502, '관리자 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.'); }
    if (!response.ok) {
        const status = response.status;
        if (fixedMessages[status]) return adminError(status, fixedMessages[status]);
        if (fallbackMessages[status]) return adminError(status, (await backendMessage(response)) ?? fallbackMessages[status]);
        return adminError(502, '관리자 서버 요청을 완료하지 못했어요.');
    }
    if (response.status === 204 || parse === null) return new Response(null, { status: 204, headers });
    try { return NextResponse.json(parse(await response.json()), { headers }); }
    catch { return adminError(502, '관리자 서버의 응답 형식을 확인할 수 없어요.'); }
}

/** JSON 객체 본문을 읽는다. 형식이 틀리면 null이다. */
export async function readAdminBody(request: Request): Promise<Record<string, unknown> | null> {
    if (!request.headers.get('content-type')?.toLowerCase().startsWith('application/json')) return null;
    try {
        const value: unknown = await request.json();
        return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : null;
    } catch { return null; }
}
