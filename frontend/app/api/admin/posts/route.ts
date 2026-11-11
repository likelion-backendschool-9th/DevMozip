import { adminQuery, parseAdminPage, withQuery } from '@/lib/administration/adminApi';
import { parseAdminComment, parseAdminPost, postQueryRules } from '@/lib/administration/adminPosts';
import { adminError, adminGuard, proxyAdmin } from '@/lib/administration/adminProxy';

// WBS 14.6 게시글·댓글 목록. target=POST(기본)·COMMENT에 맞는 형식으로 응답을 확인한다.
export async function GET(request: Request) {
    const blocked = await adminGuard(request);
    if (blocked) return blocked;
    const query = adminQuery(new URL(request.url).searchParams, postQueryRules);
    if (!query) return adminError(400, '조회 조건을 확인해 주세요.');
    if (query.get('target') === 'COMMENT' && query.has('visibility')) return adminError(400, '댓글은 공개 범위로 거를 수 없어요.');
    const parse = query.get('target') === 'COMMENT' ? parseAdminComment : parseAdminPost;
    return proxyAdmin(withQuery('/api/v1/admin/posts', query), {}, raw => parseAdminPage<unknown>(raw, parse));
}
