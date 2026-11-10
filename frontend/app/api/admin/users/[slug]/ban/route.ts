import { adminJson, memberSlug } from '@/lib/administration/adminApi';
import { checkBanBody, parseAdminMember } from '@/lib/administration/adminMembers';
import { adminError, adminGuard, proxyAdmin, readAdminBody } from '@/lib/administration/adminProxy';

// WBS 14.3 회원 제재(경고·정지·해제). 같은 사이트의 관리자 요청만 받고, 확인한 본문만 백엔드로 보낸다.
export async function PATCH(request: Request, { params }: { params: Promise<{ slug: string }> }) {
    const blocked = await adminGuard(request, true);
    if (blocked) return blocked;
    const slug = memberSlug((await params).slug);
    if (!slug) return adminError(404, '회원을 찾을 수 없어요.');
    const body = checkBanBody(await readAdminBody(request));
    if (typeof body === 'string') return adminError(400, body);
    return proxyAdmin(`/api/v1/admin/users/${encodeURIComponent(slug)}/ban`, adminJson('PATCH', body), parseAdminMember);
}
