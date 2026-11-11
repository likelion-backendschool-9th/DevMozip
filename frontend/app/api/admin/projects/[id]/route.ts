import { adminJson, checkActionBody, positiveId } from '@/lib/administration/adminApi';
import { adminError, adminGuard, proxyAdmin, readAdminBody } from '@/lib/administration/adminProxy';

// WBS 14.4 관리자 공고 삭제(D3). 성공하면 204다.
export async function PUT(request: Request, { params }: { params: Promise<{ id: string }> }) {
    const blocked = await adminGuard(request, true);
    if (blocked) return blocked;
    const id = positiveId((await params).id);
    if (!id) return adminError(404, '공고를 찾을 수 없어요.');
    const body = checkActionBody(await readAdminBody(request));
    if (!body) return adminError(400, '허용하지 않는 조치예요.');
    return proxyAdmin(`/api/v1/admin/projects/${id}`, adminJson('PUT', body), null);
}
