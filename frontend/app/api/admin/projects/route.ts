import { adminQuery, parseAdminPage, withQuery } from '@/lib/administration/adminApi';
import { parseAdminProject, projectQueryRules } from '@/lib/administration/adminProjects';
import { adminError, adminGuard, proxyAdmin } from '@/lib/administration/adminProxy';

// WBS 14.4 프로젝트 공고 목록. q·status·restricted·page·size만 확인해 백엔드로 보낸다.
export async function GET(request: Request) {
    const blocked = await adminGuard(request);
    if (blocked) return blocked;
    const query = adminQuery(new URL(request.url).searchParams, projectQueryRules);
    if (!query) return adminError(400, '조회 조건을 확인해 주세요.');
    return proxyAdmin(withQuery('/api/v1/admin/projects', query), {}, raw => parseAdminPage(raw, parseAdminProject));
}
