import 'server-only';
import { NextResponse } from 'next/server';
import { backendFetch } from '@/lib/auth/backend';

const headers = { 'Cache-Control': 'no-store' };

// 백엔드 오류 본문은 그대로 넘기지 않고 상태별 문구만 보낸다.
const messages: Record<number, string> = {
    400: '요청 내용을 확인해 주세요.',
    401: '로그인이 필요해요. 다시 로그인해 주세요.',
    403: '이 계정으로는 알림을 이용할 수 없어요.',
    404: '알림을 찾을 수 없어요.',
};

export const notificationError = (status: number, message: string) => NextResponse.json({ message }, { status, headers });

// 쓰기 요청은 같은 사이트에서만 받는다. Docker 개발 서버(--hostname 0.0.0.0)처럼 Next 내부 URL의 호스트가
// 브라우저 주소와 다를 수 있어, 프로젝트·팔로우 BFF와 같이 브라우저가 보낸 Host와 비교한다.
export function notificationRequestGuard(request: Request) {
    const url = new URL(request.url);
    let origin: URL;
    try { origin = new URL(request.headers.get('origin') ?? ''); }
    catch { return notificationError(403, '같은 사이트에서 요청해 주세요.'); }
    if (origin.host !== (request.headers.get('host') ?? url.host) || origin.protocol !== url.protocol)
        return notificationError(403, '같은 사이트에서 요청해 주세요.');
    return null;
}

/** 읽음 요청 본문. 본문이 없으면 읽음이고, 있으면 JSON 객체의 read(boolean)만 받는다. 형식이 틀리면 null이다. */
export async function readNotificationBody(request: Request): Promise<boolean | null> {
    const raw = await request.text();
    if (raw === '') return true;
    if (!request.headers.get('content-type')?.toLowerCase().startsWith('application/json')) return null;
    try {
        const value: unknown = JSON.parse(raw);
        if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
        const keys = Object.keys(value);
        const read = (value as Record<string, unknown>).read;
        return keys.length === 1 && keys[0] === 'read' && typeof read === 'boolean' ? read : null;
    } catch { return null; }
}

export async function proxyNotification<T>(path: string, init: RequestInit, parse: (body: unknown) => T, check?: (value: T) => boolean) {
    let response: Response;
    try { response = await backendFetch(path, init); }
    catch { return notificationError(502, '알림 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.'); }
    if (!response.ok) {
        const message = messages[response.status];
        return notificationError(message ? response.status : 502, message ?? '알림 서버 요청을 완료하지 못했어요.');
    }
    try {
        const value = parse(await response.json());
        if (check && !check(value)) throw new Error('요청과 응답 불일치');
        return NextResponse.json(value, { headers });
    } catch { return notificationError(502, '알림 서버의 응답 형식을 확인할 수 없어요.'); }
}
