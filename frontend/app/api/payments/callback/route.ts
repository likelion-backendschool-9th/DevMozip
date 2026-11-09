import { confirmPayment, isBillingKey, isOrderId } from '@/lib/subscription/server';
import { isCancelled } from '@/lib/subscription/portone';

// 13.2 결제 승인(모바일). 포트원 결제창이 redirectUrl로 돌려보낸다. 성공·실패 모두 화면으로 이동하며 URL에는 결과 코드만 싣는다.
// 빌링키는 이 주문(issueId)으로 발급된 것이어야 하고 주문은 로그인한 회원의 것이어야 하므로, 다른 사람이 만든 주소로는 승인되지 않는다.

const redirectTo = (path: string) => new Response(null, { status: 302, headers: { Location: path, 'Cache-Control': 'no-store' } });
const failed = (code: string) => redirectTo(`/subscription/checkout?payment=${code}`);

export async function GET(request: Request) {
    const params = new URL(request.url).searchParams;
    const orderId = params.get('orderId');
    const billingKey = params.get('billingKey');
    const code = params.get('code');
    if (code) return failed(isCancelled({ code, message: params.get('message') ?? undefined, pgCode: params.get('pgCode') ?? undefined }) ? 'cancelled' : 'failed');
    if (!isOrderId(orderId) || !isBillingKey(billingKey)) return failed('failed');
    const result = await confirmPayment(orderId, billingKey);
    return result.ok ? redirectTo('/subscription/manage?payment=success') : failed(result.code);
}
