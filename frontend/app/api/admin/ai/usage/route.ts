import { adminQuery, withQuery } from '@/lib/administration/adminApi';
import { aiUsageQueryRules, parseAiUsagePage } from '@/lib/administration/adminAi';
import { adminError, adminGuard, proxyAdmin } from '@/lib/administration/adminProxy';

// WBS 14.10 AI 사용량. month(YYYY-MM)·page·size만 확인해 보낸다. month가 없으면 백엔드가 이번 달로 본다.
export async function GET(request: Request) {
    const blocked = await adminGuard(request);
    if (blocked) return blocked;
    const query = adminQuery(new URL(request.url).searchParams, aiUsageQueryRules);
    if (!query) return adminError(400, '조회 조건을 확인해 주세요.');
    return proxyAdmin(withQuery('/api/v1/admin/ai/usage', query), {}, parseAiUsagePage);
}
