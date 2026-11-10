import { NextResponse } from 'next/server';
import { confirmPayment, isBillingKey, isOrderId, jsonError, rejectCrossSite } from '@/lib/subscription/server';

// 13.2 결제 승인(PC). 포트원 SDK가 돌려준 빌링키를 백엔드로 넘기고, 성공하면 토큰을 재발급한다.
export async function POST(request: Request) {
    const rejected = rejectCrossSite(request);
    if (rejected) return rejected;
    let input: unknown;
    try { input = await request.json(); } catch { return jsonError(400, '입력 내용을 확인해 주세요.'); }
    const { orderId, billingKey } = (input && typeof input === 'object' ? input : {}) as Record<string, unknown>;
    if (!isOrderId(orderId) || !isBillingKey(billingKey)) return jsonError(400, '결제 정보를 확인해 주세요.');
    const result = await confirmPayment(orderId, billingKey);
    if (!result.ok) return NextResponse.json({ message: result.message, code: result.code }, { status: result.status, headers: { 'Cache-Control': 'no-store' } });
    return NextResponse.json({ ok: true }, { headers: { 'Cache-Control': 'no-store' } });
}
