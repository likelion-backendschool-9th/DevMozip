import 'server-only';
import { NextResponse } from 'next/server';
import { backendFetch } from '@/lib/auth/backend';
const headers = { 'Cache-Control': 'no-store' };
export const portfolioError = (status: number, message: string) => NextResponse.json({ message }, { status, headers });
export async function proxyPortfolio(path: string, parse: (body: unknown) => unknown) {
    try {
        const response = await backendFetch(path);
        if (!response.ok) {
            const messages: Record<number, string> = { 400: '조회 조건을 확인해 주세요.', 401: '로그인해 주세요.',
                403: '포트폴리오 조회 권한을 확인해 주세요.', 404: '포트폴리오 정보를 찾을 수 없어요.',
                503: '포트폴리오를 불러올 수 없어요. 잠시 후 다시 시도해 주세요.' };
            return portfolioError(messages[response.status] ? response.status : 502, messages[response.status] || '포트폴리오 요청을 완료하지 못했어요.');
        }
        return NextResponse.json(parse(await response.json()), { headers });
    } catch { return portfolioError(502, '포트폴리오 서버 연결 또는 응답을 확인할 수 없어요.'); }
}
