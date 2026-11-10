import { NextResponse } from 'next/server';
import { backendFetch } from '@/lib/auth/backend';
import { validProfileSlug } from '@/lib/profiles/profileDetail';
import { likedPostsPagination } from '@/lib/profiles/likedPostsApi';
import { parseProfilePostsPage } from '@/lib/profiles/profilePostsApi';
const headers = { 'Cache-Control': 'no-store' };
const error = (status: number, message: string) => NextResponse.json({ message }, { status, headers });
export async function GET(request: Request, context: { params: Promise<{ slug: string }> }) {
    const { slug } = await context.params;
    if (slug !== 'me' && !validProfileSlug(slug)) return error(400, '프로필 주소를 확인해 주세요.');
    const pagination = likedPostsPagination(new URL(request.url).searchParams);
    if (!pagination) return error(400, '페이지와 조회 개수를 확인해 주세요.');
    const { page, size } = pagination;
    try {
        const response = await backendFetch(`/api/v1/users/${encodeURIComponent(slug)}/posts?page=${page}&size=${size}`);
        if (!response.ok) {
            const messages: Record<number, string> = { 400: '조회 조건을 확인해 주세요.', 401: '다시 로그인해 주세요.',
                403: '포스팅 조회 권한이 없습니다.', 404: '프로필 정보를 찾을 수 없어요.', 503: '포스팅을 불러올 수 없어요. 잠시 후 다시 시도해 주세요.' };
            return error(messages[response.status] ? response.status : 502, messages[response.status] || '포스팅 서버 응답을 확인할 수 없어요.');
        }
        const value = parseProfilePostsPage(await response.json());
        if (value.page !== page || value.size !== size || (slug !== 'me' && value.items.some(p => p.author.tag !== slug)))
            return error(502, '요청한 프로필 또는 페이지와 응답이 다릅니다.');
        return NextResponse.json(value, { headers });
    } catch { return error(502, '포스팅 서버 연결 또는 응답을 확인할 수 없어요.'); }
}
