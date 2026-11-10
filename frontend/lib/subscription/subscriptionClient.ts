import { requestBillingKey } from './portone';
import { failureCodeForStatus } from './subscriptionView';
import type { CheckoutResult, CheckoutStart, MySubscription, PaymentHistoryPage, PaymentMethodSummary, PaymentProvider, SubscriptionPlan } from '@/types/subscription';

// 구독·결제 화면이 쓰는 BFF 호출. 백엔드 계약은 docs/premium-payment-api.md에 있다.
// 1단계(Mock)와 같은 함수 이름을 유지해 화면 코드는 바꾸지 않았다.

export const PAGE_SIZE = 20;
const LOAD_ERROR = '정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';

async function readJson<T>(response: Response): Promise<T> {
    if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(typeof body?.message === 'string' ? body.message : LOAD_ERROR);
    }
    return response.json() as Promise<T>;
}

const get = (path: string) => fetch(path, { cache: 'no-store' });
const post = (path: string, body?: unknown) => fetch(path, {
    method: 'POST', cache: 'no-store',
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? '{}' : JSON.stringify(body),
});

export async function fetchPlans(): Promise<SubscriptionPlan[]> {
    return readJson(await get('/api/subscription/plans'));
}

export async function fetchMySubscription(): Promise<MySubscription> {
    return readJson(await get('/api/subscription/me'));
}

export async function fetchPaymentHistory(page: number): Promise<PaymentHistoryPage> {
    return readJson(await get(`/api/payments/me?page=${encodeURIComponent(page)}&size=${PAGE_SIZE}`));
}

export async function fetchPaymentMethod(): Promise<PaymentMethodSummary | null> {
    const response = await get('/api/payment-methods/me');
    if (response.status === 204) return null;
    return readJson(response);
}

/**
 * 결제 준비 → 포트원 결제창 → 결제 승인.
 * 모바일은 결제창이 결과 주소(/api/payments/callback)로 이동하므로 redirecting을 돌려주고 이 함수는 끝난다.
 */
export async function startSubscription(planId: number, provider: PaymentProvider): Promise<CheckoutResult> {
    const prepared = await post('/api/payments/checkout', { planId, provider });
    if (!prepared.ok) return { status: 'failed', code: failureCodeForStatus(prepared.status) };
    const start = await prepared.json() as CheckoutStart;

    let issued;
    try {
        issued = await requestBillingKey(start);
    } catch {
        return { status: 'failed', code: 'unavailable' };
    }
    if (issued.status === 'redirecting') return { status: 'redirecting' };
    if (issued.status === 'cancelled') return { status: 'failed', code: 'cancelled' };
    if (issued.status === 'failed') return { status: 'failed', code: 'failed' };

    const confirmed = await post('/api/payments/confirm', { orderId: start.orderId, billingKey: issued.billingKey });
    return confirmed.ok ? { status: 'success' } : { status: 'failed', code: failureCodeForStatus(confirmed.status) };
}

/** 해지: 다음 결제를 멈추고 결제 수단을 지운다. 기간 끝까지 프리미엄은 유지된다 */
export async function cancelSubscription(): Promise<MySubscription> {
    return readJson(await post('/api/subscription/cancel'));
}
