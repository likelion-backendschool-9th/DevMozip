import { NextResponse } from 'next/server';
import { backendFetch } from '@/lib/auth/backend';
import { likedPostsPagination, parseLikedPostsPage } from '@/lib/profiles/likedPostsApi';
const headers = { 'Cache-Control': 'no-store' };
const error = (status: number, message: string) => NextResponse.json({ message }, { status, headers });
export async function GET(request: Request) {
    const pagination = likedPostsPagination(new URL(request.url).searchParams);
    if (!pagination) return error(400, '페이지와 조회 개수를 확인해 주세요.');
    const { page, size } = pagination;
    let response: Response;
    try { response = await backendFetch(`/api/v1/users/me/liked-posts?page=${page}&size=${size}`); }
    catch { return error(502, '목록 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.'); }
    const messages: Record<number, string> = { 400: '페이지와 조회 개수를 확인해 주세요.', 401: '다시 로그인해 주세요.',
        403: '이 계정으로 목록을 조회할 수 없어요.', 404: '회원 정보를 찾을 수 없어요.', 503: '좋아요한 글을 불러올 수 없어요. 잠시 후 다시 시도해 주세요.' };
    if (!response.ok) return error(messages[response.status] ? response.status : 502, messages[response.status] || '목록 요청을 완료하지 못했어요.');
    try {
        const value = parseLikedPostsPage(await response.json());
        if (value.page !== page || value.size !== size) throw new Error('페이지 불일치');
        return NextResponse.json(value, { headers });
    } catch { return error(502, '좋아요 목록 응답을 확인할 수 없어요.'); }
}
