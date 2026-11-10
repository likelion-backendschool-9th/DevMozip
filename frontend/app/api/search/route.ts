import { NextResponse } from 'next/server';
import { backendFetch } from '@/lib/auth/backend';
import { parseSearchResult, SEARCH_PAGE_SIZE, searchProxyQuery } from '@/lib/search/searchApi';

const headers = { 'Cache-Control': 'no-store' };
const error = (status: number, message: string) => NextResponse.json({ message }, { status, headers });

// 백엔드 오류 본문은 그대로 넘기지 않고 상태별 문구만 보낸다.
const messages: Record<number, string> = {
    400: '검색 조건을 확인해 주세요.',
    401: '로그인이 필요해요. 다시 로그인해 주세요.',
};

/**
 * 통합 검색(WBS 6.1~6.10) BFF. 허용한 쿼리만 GET /api/v1/search로 넘기고, 로그인 상태면 backendFetch가 토큰을 붙인다.
 * 비로그인도 검색할 수 있다. 사용자 ID는 보내지 않는다.
 */
export async function GET(request: Request) {
    const query = searchProxyQuery(new URL(request.url).searchParams);
    if (!query) return error(400, messages[400]);
    let response: Response;
    try { response = await backendFetch(`/api/v1/search?${query}`); }
    catch { return error(502, '검색 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.'); }
    if (!response.ok) {
        const message = messages[response.status];
        return error(message ? response.status : 502, message ?? '검색 서버 요청을 완료하지 못했어요.');
    }
    try {
        const result = parseSearchResult(query.get('type') === 'projects' ? 'projects' : 'developers', await response.json());
        const page = Number(query.get('page') ?? 0), size = Number(query.get('size') ?? SEARCH_PAGE_SIZE);
        if (result.page.page !== page || result.page.size !== size) throw new Error('요청과 응답 불일치');
        return NextResponse.json(result.page, { headers });
    } catch { return error(502, '검색 서버의 응답 형식을 확인할 수 없어요.'); }
}
