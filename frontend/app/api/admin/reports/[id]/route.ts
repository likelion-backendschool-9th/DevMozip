import { adminJson, positiveId } from '@/lib/administration/adminApi';
import { checkHandleBody, parseAdminReport } from '@/lib/administration/adminReports';
import { adminError, adminGuard, proxyAdmin, readAdminBody } from '@/lib/administration/adminProxy';

// WBS 14.8 신고 처리. 처리자는 본문으로 받지 않고 백엔드가 인증 정보로 정한다.
export async function PATCH(request: Request, { params }: { params: Promise<{ id: string }> }) {
    const blocked = await adminGuard(request, true);
    if (blocked) return blocked;
    const id = positiveId((await params).id);
    if (!id) return adminError(404, '신고를 찾을 수 없어요.');
    const body = checkHandleBody(await readAdminBody(request));
    if (typeof body === 'string') return adminError(400, body);
    return proxyAdmin(`/api/v1/admin/reports/${id}`, adminJson('PATCH', body), parseAdminReport);
}
