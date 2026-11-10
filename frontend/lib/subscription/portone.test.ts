import { describe, expect, it, vi } from 'vitest';
import { isCancelled, requestBillingKey } from './portone';
import type { CheckoutStart } from '@/types/subscription';

const start: CheckoutStart = { orderId: 'order-1', provider: 'TOSS_PAYMENTS', storeId: 'store-test', channelKey: 'channel-key-toss', billingKeyMethod: 'CARD',
    issueName: 'DevMozip 프리미엄 월 구독', customerId: 'customer-1', redirectUrl: 'https://devmozip.test/api/payments/callback?orderId=order-1' };
const sdk = (response: unknown) => ({ requestIssueBillingKey: vi.fn().mockResolvedValue(response) });

describe('포트원 빌링키 발급 요청', () => {
    it('주문 번호를 issueId로, 구매자 ID를 customer.customerId로 넘긴다', async () => {
        const portOne = sdk({ billingKey: 'billing-key-1' });
        expect(await requestBillingKey(start, portOne)).toEqual({ status: 'issued', billingKey: 'billing-key-1' });
        expect(portOne.requestIssueBillingKey).toHaveBeenCalledWith({
            storeId: 'store-test', channelKey: 'channel-key-toss', billingKeyMethod: 'CARD', issueId: 'order-1',
            issueName: 'DevMozip 프리미엄 월 구독', customer: { customerId: 'customer-1' }, redirectUrl: start.redirectUrl,
        });
    });
    it('오류 코드가 오면 취소와 실패를 구분한다', async () => {
        expect(await requestBillingKey(start, sdk({ code: 'FAILURE_TYPE_PG', pgCode: 'PAY_PROCESS_CANCELED' }))).toEqual({ status: 'cancelled' });
        expect(await requestBillingKey(start, sdk({ code: 'FAILURE_TYPE_PG', message: '사용자가 결제를 취소하였습니다' }))).toEqual({ status: 'cancelled' });
        expect(await requestBillingKey(start, sdk({ code: 'FAILURE_TYPE_PG', message: '카드 한도 초과' }))).toEqual({ status: 'failed' });
        expect(await requestBillingKey(start, sdk({}))).toEqual({ status: 'failed' });
    });
    it('결과 없이 끝나면 모바일 결과 주소로 이동 중이다', async () => {
        expect(await requestBillingKey(start, sdk(undefined))).toEqual({ status: 'redirecting' });
    });
    it('취소 표시를 코드·메시지에서 찾는다', () => {
        expect(isCancelled({ code: 'USER_CANCEL' })).toBe(true);
        expect(isCancelled({ code: 'FAILURE_TYPE_PG' })).toBe(false);
    });
});
