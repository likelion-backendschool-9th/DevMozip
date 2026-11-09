import { beforeEach, describe, expect, it, vi } from 'vitest';
const doubles = vi.hoisted(() => ({ requestBillingKey: vi.fn() }));
vi.mock('./portone', () => ({ requestBillingKey: doubles.requestBillingKey }));
import { cancelSubscription, fetchPaymentHistory, fetchPaymentMethod, startSubscription } from './subscriptionClient';

const start = { orderId: 'order-1', provider: 'KAKAO_PAY', storeId: 'store-test', channelKey: 'channel-key-kakao', billingKeyMethod: 'EASY_PAY',
    issueName: 'DevMozip 프리미엄 월 구독', customerId: 'customer-1', redirectUrl: 'https://devmozip.test/api/payments/callback?orderId=order-1' };
const json = (status: number, body: unknown) => new Response(body === null ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
let fetchMock: ReturnType<typeof vi.fn>;
const calls = () => fetchMock.mock.calls.map(([url, init]) => [url, init?.method ?? 'GET', init?.body]);

beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    doubles.requestBillingKey.mockReset();
});

describe('결제 흐름 (BFF + 포트원 SDK)', () => {
    it('결제 준비 → 빌링키 발급 → 승인 순서로 호출하고 금액은 보내지 않는다', async () => {
        fetchMock.mockResolvedValueOnce(json(201, start)).mockResolvedValueOnce(json(200, { ok: true }));
        doubles.requestBillingKey.mockResolvedValue({ status: 'issued', billingKey: 'billing-key-1' });
        expect(await startSubscription(1, 'KAKAO_PAY')).toEqual({ status: 'success' });
        expect(doubles.requestBillingKey).toHaveBeenCalledWith(start);
        expect(calls()).toEqual([
            ['/api/payments/checkout', 'POST', JSON.stringify({ planId: 1, provider: 'KAKAO_PAY' })],
            ['/api/payments/confirm', 'POST', JSON.stringify({ orderId: 'order-1', billingKey: 'billing-key-1' })],
        ]);
    });
    it('결제창을 닫으면 취소, 오류면 실패로 끝내고 승인하지 않는다', async () => {
        // 응답 본문은 한 번만 읽을 수 있으므로 호출마다 새로 만든다.
        fetchMock.mockImplementation(async () => json(201, start));
        doubles.requestBillingKey.mockResolvedValueOnce({ status: 'cancelled' }).mockResolvedValueOnce({ status: 'failed' });
        expect(await startSubscription(1, 'KAKAO_PAY')).toEqual({ status: 'failed', code: 'cancelled' });
        expect(await startSubscription(1, 'KAKAO_PAY')).toEqual({ status: 'failed', code: 'failed' });
        expect(calls().every(([url]) => url === '/api/payments/checkout')).toBe(true);
    });
    it('모바일 결제창으로 이동하면 승인은 결과 주소가 맡는다', async () => {
        fetchMock.mockResolvedValue(json(201, start));
        doubles.requestBillingKey.mockResolvedValue({ status: 'redirecting' });
        expect(await startSubscription(1, 'TOSS_PAYMENTS')).toEqual({ status: 'redirecting' });
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });
    it('SDK를 불러오지 못하면 지금은 결제할 수 없다고 안내한다', async () => {
        fetchMock.mockResolvedValue(json(201, start));
        doubles.requestBillingKey.mockRejectedValue(new Error('portone-sdk-load-failed'));
        expect(await startSubscription(1, 'TOSS_PAYMENTS')).toEqual({ status: 'failed', code: 'unavailable' });
    });
    it.each([[409, 'already-subscribed'], [403, 'not-allowed'], [503, 'unavailable'], [401, 'login']] as const)('결제 준비 %i은 %s', async (status, code) => {
        fetchMock.mockResolvedValue(json(status, { message: 'x' }));
        expect(await startSubscription(1, 'TOSS_PAYMENTS')).toEqual({ status: 'failed', code });
        expect(doubles.requestBillingKey).not.toHaveBeenCalled();
    });
    it.each([[400, 'failed'], [404, 'expired'], [409, 'already-subscribed'], [502, 'unknown']] as const)('승인 %i은 %s', async (status, code) => {
        fetchMock.mockResolvedValueOnce(json(201, start)).mockResolvedValueOnce(json(status, { message: 'x' }));
        doubles.requestBillingKey.mockResolvedValue({ status: 'issued', billingKey: 'billing-key-1' });
        expect(await startSubscription(1, 'KAKAO_PAY')).toEqual({ status: 'failed', code });
    });
});

describe('조회와 해지', () => {
    it('결제 수단이 없으면(204) null이다', async () => {
        fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })).mockResolvedValueOnce(json(200, { provider: 'KAKAO_PAY', display: '카카오페이' }));
        expect(await fetchPaymentMethod()).toBeNull();
        expect(await fetchPaymentMethod()).toEqual({ provider: 'KAKAO_PAY', display: '카카오페이' });
    });
    it('결제 내역은 페이지와 크기를 붙여 요청한다', async () => {
        fetchMock.mockResolvedValue(json(200, { items: [], page: 2, size: 20, hasNext: false }));
        await fetchPaymentHistory(2);
        expect(calls()[0][0]).toBe('/api/payments/me?page=2&size=20');
    });
    it('해지 실패는 서버 문구로 알린다', async () => {
        fetchMock.mockResolvedValue(json(409, { message: '해지할 수 있는 구독이 없습니다.' }));
        await expect(cancelSubscription()).rejects.toThrow('해지할 수 있는 구독이 없습니다.');
        expect(calls()[0].slice(0, 2)).toEqual(['/api/subscription/cancel', 'POST']);
    });
});
