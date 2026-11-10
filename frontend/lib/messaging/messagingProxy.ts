import 'server-only';
import { NextResponse } from 'next/server';
import { backendFetch } from '@/lib/auth/backend';

const headers = { 'Cache-Control': 'no-store' };
const defaultMessages: Record<number, string> = {
    400: '입력 내용을 확인해 주세요.', 401: '로그인이 만료됐어요. 다시 로그인해 주세요.',
    403: '이 요청을 처리할 권한이 없어요.', 404: '대화를 찾을 수 없어요.',
    409: '지금은 이 요청을 처리할 수 없어요.',
};

export const messagingError = (status: number, message: string) => NextResponse.json({ message }, { status, headers });

/**
 * Origin의 호스트가 브라우저가 연 주소의 호스트인지 본다. Next 서버 액션과 같이 X-Forwarded-Host나 Host와 비교한다.
 * `request.url`은 서버가 듣는 주소(컨테이너의 HOSTNAME=0.0.0.0)로 만들어져 브라우저 주소와 다르므로 쓰지 않는다.
 */
function sameSiteOrigin(request: Request) {
    const origin = request.headers.get('origin');
    if (!origin) return false;
    let host: string;
    try {
        host = new URL(origin).host;
    } catch {
        return false;
    }
    if (!host) return false;
    const forwarded = request.headers.get('x-forwarded-host')?.split(',')[0]?.trim();
    return host === forwarded || host === request.headers.get('host');
}

/** 쿠키가 자동으로 전송되므로 쓰기 요청은 같은 사이트의 JSON 요청만 받는다. */
export function messagingWriteGuard(request: Request) {
    if (!sameSiteOrigin(request)) return messagingError(403, '같은 사이트에서 요청해 주세요.');
    if (!request.headers.get('content-type')?.startsWith('application/json')) return messagingError(415, 'JSON 형식이 필요합니다.');
    return null;
}

/** 객체가 아니거나 JSON이 깨졌으면 null이다. */
export async function readJsonObject(request: Request): Promise<Record<string, unknown> | null> {
    try {
        const body = await request.json();
        return body && typeof body === 'object' && !Array.isArray(body) ? body as Record<string, unknown> : null;
    } catch { return null; }
}

/** 허용한 파라미터만 백엔드로 넘긴다. */
export function forwardedQuery(request: Request, names: string[]) {
    const source = new URL(request.url).searchParams;
    const query = new URLSearchParams();
    for (const name of names) {
        const value = source.get(name);
        if (value !== null) query.set(name, value);
    }
    const text = query.toString();
    return text ? `?${text}` : '';
}

export const jsonRequest = (method: string, body: unknown): RequestInit => ({
    method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });

/**
 * 백엔드 호출 결과를 브라우저 응답으로 바꾼다. parse가 없으면 본문 없이 상태만 돌려준다.
 * 내부 오류 내용은 전달하지 않고 경로별 안내 문구(messages)나 기본 문구만 내려보낸다.
 */
export async function proxyMessaging(path: string, init: RequestInit, parse?: (body: unknown) => unknown, messages: Record<number, string> = {}) {
    let response: Response;
    try {
        response = await backendFetch(path, init);
    } catch { return messagingError(502, '메시지 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.'); }
    if (!response.ok) {
        const message = messages[response.status] ?? defaultMessages[response.status];
        return message ? messagingError(response.status, message) : messagingError(502, '메시지 서버 요청을 완료하지 못했어요.');
    }
    if (!parse) return new NextResponse(null, { status: response.status, headers });
    try {
        return NextResponse.json(parse(await response.json()), { status: response.status, headers });
    } catch { return messagingError(502, '메시지 서버의 응답 형식을 확인할 수 없어요.'); }
}
