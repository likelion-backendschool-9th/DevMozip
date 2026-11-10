import { forward, jsonError, rejectCrossSite } from '@/lib/subscription/server';

// 13.2 결제 준비. 상품과 결제 수단만 넘기고 금액·사용자 ID는 보내지 않는다.
export async function POST(request: Request) {
    const rejected = rejectCrossSite(request);
    if (rejected) return rejected;
    let input: unknown;
    try { input = await request.json(); } catch { return jsonError(400, '입력 내용을 확인해 주세요.'); }
    const { planId, provider } = (input && typeof input === 'object' ? input : {}) as Record<string, unknown>;
    if (typeof planId !== 'number' || !Number.isSafeInteger(planId) || planId < 1) return jsonError(400, '상품을 확인해 주세요.');
    if (provider !== 'TOSS_PAYMENTS' && provider !== 'KAKAO_PAY') return jsonError(400, '결제 수단을 선택해 주세요.');
    return forward('/api/v1/payments/checkout', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ planId, provider }),
    });
}
