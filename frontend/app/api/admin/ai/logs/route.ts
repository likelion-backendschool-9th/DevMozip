import { adminQuery, parseAdminPage, withQuery } from '@/lib/administration/adminApi';
import { parseAiFailure } from '@/lib/administration/adminAi';
import { adminError, adminGuard, proxyAdmin } from '@/lib/administration/adminProxy';

// WBS 14.11 AI 추천 실패 기록(D6). page·size만 보낸다.
export async function GET(request: Request) {
    const blocked = await adminGuard(request);
    if (blocked) return blocked;
    const query = adminQuery(new URL(request.url).searchParams, {});
    if (!query) return adminError(400, '조회 조건을 확인해 주세요.');
    return proxyAdmin(withQuery('/api/v1/admin/ai/logs', query), {}, raw => parseAdminPage(raw, parseAiFailure));
}
