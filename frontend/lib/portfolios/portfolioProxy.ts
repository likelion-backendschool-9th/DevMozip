import 'server-only';
import { NextResponse } from 'next/server';
import { backendFetch } from '@/lib/auth/backend';
const headers = { 'Cache-Control': 'no-store' };
export const portfolioError = (status: number, message: string) => NextResponse.json({ message }, { status, headers });
export async function proxyPortfolio(path: string, parse: (body: unknown) => unknown, init?: RequestInit) {
    try {
        const response = await (init ? backendFetch(path, init) : backendFetch(path));
        if (!response.ok) {
            const messages: Record<number, string> = { 400: '조회 조건을 확인해 주세요.', 401: '로그인해 주세요.',
                403: '포트폴리오 조회 권한을 확인해 주세요.', 404: '포트폴리오 정보를 찾을 수 없어요.',
                409: '다른 곳에서 변경되었거나 프로젝트 지원서에서 참조 중입니다. 최신 정보와 연결 상태를 확인해 주세요.',
                413: '문서 크기는 2MB 이내여야 합니다.', 503: '저장소 또는 지원서 참조 제약이 준비되지 않았어요. 잠시 후 다시 시도해 주세요.' };
            return portfolioError(messages[response.status] ? response.status : 502, messages[response.status] || '포트폴리오 요청을 완료하지 못했어요.');
        }
        if (init?.method === 'DELETE' && response.status === 204) return new NextResponse(null, { status: 204, headers });
        if (response.status !== 200 && response.status !== 201) return portfolioError(502, '저장 결과를 확인할 수 없어요.');
        return NextResponse.json(parse(await response.json()), { status: response.status, headers });
    } catch { return portfolioError(502, '포트폴리오 서버 연결 또는 응답을 확인할 수 없어요.'); }
}
