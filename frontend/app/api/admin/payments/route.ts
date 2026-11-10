import { adminQuery, withQuery } from '@/lib/administration/adminApi';
import { parsePaymentPage, paymentQueryRules } from '@/lib/administration/adminBilling';
import { adminError, adminGuard, proxyAdmin } from '@/lib/administration/adminProxy';

// WBS 14.13 결제 관리. status·page·size만 확인해 보낸다. 빌링키는 백엔드 응답에도 없다.
export async function GET(request: Request) {
    const blocked = await adminGuard(request);
    if (blocked) return blocked;
    const query = adminQuery(new URL(request.url).searchParams, paymentQueryRules);
    if (!query) return adminError(400, '조회 조건을 확인해 주세요.');
    return proxyAdmin(withQuery('/api/v1/admin/payments', query), {}, parsePaymentPage);
}
