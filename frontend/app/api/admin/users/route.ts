import { adminQuery, parseAdminPage, withQuery } from '@/lib/administration/adminApi';
import { memberQueryRules, parseAdminMember } from '@/lib/administration/adminMembers';
import { adminError, adminGuard, proxyAdmin } from '@/lib/administration/adminProxy';

// WBS 14.2 회원 조회. q·status·role·page·size만 확인해 백엔드로 보낸다.
export async function GET(request: Request) {
    const blocked = await adminGuard(request);
    if (blocked) return blocked;
    const query = adminQuery(new URL(request.url).searchParams, memberQueryRules);
    if (!query) return adminError(400, '조회 조건을 확인해 주세요.');
    return proxyAdmin(withQuery('/api/v1/admin/users', query), {}, raw => parseAdminPage(raw, parseAdminMember));
}
