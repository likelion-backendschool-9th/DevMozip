import { adminQuery, withQuery } from '@/lib/administration/adminApi';
import { parseSubscriptionPage, subscriptionQueryRules } from '@/lib/administration/adminBilling';
import { adminError, adminGuard, proxyAdmin } from '@/lib/administration/adminProxy';

// WBS 14.12 구독 관리. status·page·size만 확인해 보낸다.
export async function GET(request: Request) {
    const blocked = await adminGuard(request);
    if (blocked) return blocked;
    const query = adminQuery(new URL(request.url).searchParams, subscriptionQueryRules);
    if (!query) return adminError(400, '조회 조건을 확인해 주세요.');
    return proxyAdmin(withQuery('/api/v1/admin/subscriptions', query), {}, parseSubscriptionPage);
}
