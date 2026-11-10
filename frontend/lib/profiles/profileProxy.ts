import 'server-only';
import { NextResponse } from 'next/server';
import { backendFetch } from '@/lib/auth/backend';
const headers = { 'Cache-Control': 'no-store' };
export const profileError = (status: number, message: string) => NextResponse.json({ message }, { status, headers });
export function profileRequestGuard(request?: Request) {
    if (!request) return null;
    // Docker 내부 URL 대신 브라우저가 보낸 Host를 비교한다.
    // 임의 x-forwarded-host는 출처 판단에 사용하지 않는다.
    const target = new URL(request.url);
    const host = request.headers.get('host');
    if (host) {
        try {
            const external = new URL(`${target.protocol}//${host}`);
            if (external.host !== host || external.username || external.password || external.pathname !== '/' || external.search || external.hash) throw new Error('host');
            target.hostname = external.hostname;
            target.port = external.port;
        } catch { return profileError(403, '같은 사이트에서 요청해 주세요.'); }
    }
    if (request.headers.get('origin') !== target.origin) return profileError(403, '같은 사이트에서 요청해 주세요.');
    return null;
}
const messages: Record<number, string> = {
    400: '입력 내용을 확인해 주세요.', 401: '로그인이 만료됐어요. 다시 로그인해 주세요.',
    403: '이 계정으로 수정할 수 없어요.', 404: '프로필 정보를 찾을 수 없어요.',
    409: '닉네임 또는 태그가 이미 사용 중이에요.', 413: '이미지는 256KB 이하로 선택해 주세요.',
    415: 'PNG·JPEG·WebP 이미지를 선택해 주세요.', 503: '이미지 저장소를 사용할 수 없어요.',
};
export async function proxyProfile(path: string, init: RequestInit, parse: (body: unknown) => unknown) {
    try {
        const response = await backendFetch(path, init);
        if (!response.ok) return profileError(messages[response.status] ? response.status : 502, messages[response.status] ?? '프로필 서버 요청을 완료하지 못했어요.');
        try { return NextResponse.json(parse(await response.json()), { headers }); }
        catch { return profileError(502, '프로필 서버의 응답 형식을 확인할 수 없어요.'); }
    } catch { return profileError(502, '프로필 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.'); }
}
