import { adminJson, checkRestrictBody, positiveId } from '@/lib/administration/adminApi';
import { parseAdminPost } from '@/lib/administration/adminPosts';
import { adminError, adminGuard, proxyAdmin, readAdminBody } from '@/lib/administration/adminProxy';

// WBS 14.7 게시글 관리자 숨김·복원. 댓글은 숨김 상태가 없어 받지 않는다(D4).
export async function PATCH(request: Request, { params }: { params: Promise<{ id: string }> }) {
    const blocked = await adminGuard(request, true);
    if (blocked) return blocked;
    const id = positiveId((await params).id);
    if (!id) return adminError(404, '게시글을 찾을 수 없어요.');
    if (new URL(request.url).searchParams.get('target') === 'COMMENT') return adminError(400, '댓글은 숨김 상태가 없어요. 삭제를 이용해 주세요.');
    const body = checkRestrictBody(await readAdminBody(request));
    if (!body) return adminError(400, '숨김 여부를 확인해 주세요.');
    return proxyAdmin(`/api/v1/admin/posts/${id}/visibility`, adminJson('PATCH', body), parseAdminPost);
}
