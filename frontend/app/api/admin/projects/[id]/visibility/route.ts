import { adminJson, checkRestrictBody, positiveId } from '@/lib/administration/adminApi';
import { parseAdminProject } from '@/lib/administration/adminProjects';
import { adminError, adminGuard, proxyAdmin, readAdminBody } from '@/lib/administration/adminProxy';

// WBS 14.5 공고 관리자 숨김·복원(D2).
export async function PATCH(request: Request, { params }: { params: Promise<{ id: string }> }) {
    const blocked = await adminGuard(request, true);
    if (blocked) return blocked;
    const id = positiveId((await params).id);
    if (!id) return adminError(404, '공고를 찾을 수 없어요.');
    const body = checkRestrictBody(await readAdminBody(request));
    if (!body) return adminError(400, '숨김 여부를 확인해 주세요.');
    return proxyAdmin(`/api/v1/admin/projects/${id}/visibility`, adminJson('PATCH', body), parseAdminProject);
}
