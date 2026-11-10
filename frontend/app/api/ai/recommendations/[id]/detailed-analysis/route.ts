import { NextResponse } from 'next/server';
import { backendFetch } from '@/lib/auth/backend';
import { parsePortfolioAnalysis } from '@/lib/matching/portfolioAnalysis';
const headers = { 'Cache-Control': 'no-store' };
const error = (status: number, message: string) => NextResponse.json({ message }, { status, headers });
type Context = { params: Promise<{ id: string }> };
async function handle(request: Request, context: Context) {
    const { id } = await context.params;
    const url = new URL(request.url);
    if (!/^[1-9][0-9]*$/.test(id) || !Number.isSafeInteger(Number(id)) || url.search) return error(400, '추천 결과 ID를 확인해 주세요.');
    const init: RequestInit = { method: request.method, signal: AbortSignal.timeout(190000) };
    if (request.method === 'POST') {
        if (request.headers.get('origin') !== `${url.protocol}//${request.headers.get('host') || url.host}`) return error(403, '같은 사이트에서 요청해 주세요.');
        if (!request.headers.get('content-type')?.startsWith('application/json')) return error(415, 'JSON 형식이 필요합니다.');
        try {
            const body = await request.json();
            if (!body || Array.isArray(body) || typeof body !== 'object' || Object.keys(body).length) return error(400, '추천 결과 ID만 사용해 주세요.');
        } catch { return error(400, 'JSON 내용을 확인해 주세요.'); }
        init.headers = { 'Content-Type': 'application/json' }; init.body = '{}';
    }
    try {
        const response = await backendFetch(`/api/v1/ai/recommendations/${id}/detailed-analysis`, init);
        if (!response.ok) {
            const messages: Record<number, string> = {
                400: '개발자 추천 결과를 선택해 주세요.', 401: '다시 로그인해 주세요.',
                403: '유효한 프리미엄 구독이 필요합니다. 만료 여부를 확인해 주세요.', 404: '추천 결과 또는 공개 개발자를 확인할 수 없습니다.',
                409: '분석 중 자료나 요청 상태가 변경되었습니다. 다시 시도해 주세요.', 429: 'AI 요청 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.',
                502: 'AI 분석 응답을 확인할 수 없습니다. 다시 시도해 주세요.', 503: '분석 설정 또는 자료 저장소를 확인할 수 없습니다.',
                504: '분석 시간이 초과되었습니다. 다시 시도해 주세요.'
            };
            return error(messages[response.status] ? response.status : 502, messages[response.status] || messages[502]);
        }
        return NextResponse.json(parsePortfolioAnalysis(await response.json(), Number(id)),
            { status: response.status, headers: { ...headers, ...(response.status === 202 ? { 'Retry-After': '3' } : {}) } });
    } catch (failure) {
        const timeout = failure instanceof Error && ['TimeoutError', 'AbortError'].includes(failure.name);
        return error(timeout ? 504 : 502, timeout ? '분석 시간이 초과되었습니다. 다시 조회해 주세요.' : '상세 분석 서버 연결 또는 응답을 확인할 수 없습니다.');
    }
}
export const GET = handle;
export const POST = handle;
