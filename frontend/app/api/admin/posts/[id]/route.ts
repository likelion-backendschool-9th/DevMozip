import { adminJson, checkActionBody, positiveId } from '@/lib/administration/adminApi';
import { postTarget } from '@/lib/administration/adminPosts';
import { adminError, adminGuard, proxyAdmin, readAdminBody } from '@/lib/administration/adminProxy';

// WBS 14.6 관리자 삭제(D3). target=COMMENT면 댓글을 삭제한다. 성공하면 204다.
export async function PUT(request: Request, { params }: { params: Promise<{ id: string }> }) {
    const blocked = await adminGuard(request, true);
    if (blocked) return blocked;
    const id = positiveId((await params).id);
    if (!id) return adminError(404, '대상을 찾을 수 없어요.');
    const search = new URL(request.url).searchParams;
    if (search.getAll('target').length > 1 || (search.has('target') && !['POST', 'COMMENT'].includes(search.get('target')!)))
        return adminError(400, '대상을 확인해 주세요.');
    const body = checkActionBody(await readAdminBody(request));
    if (!body) return adminError(400, '허용하지 않는 조치예요.');
    return proxyAdmin(`/api/v1/admin/posts/${id}?target=${postTarget(search)}`, adminJson('PUT', body), null);
}
