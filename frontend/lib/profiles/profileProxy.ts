import 'server-only';
import { NextResponse } from 'next/server';
import { authMode } from '@/lib/auth/env';
import { backendFetch } from '@/lib/auth/backend';
const headers = { 'Cache-Control': 'no-store' };
export const profileError = (status: number, message: string) => NextResponse.json({ message }, { status, headers });
export function profileRequestGuard(request?: Request) {
    if (authMode() === 'mock') return profileError(404, '데모 모드에서는 서버 프로필 수정을 사용하지 않습니다.');
    if (request && request.headers.get('origin') !== new URL(request.url).origin) return profileError(403, '같은 사이트에서 요청해 주세요.');
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
        return NextResponse.json(parse(await response.json()), { headers });
    } catch { return profileError(502, '프로필 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.'); }
}
