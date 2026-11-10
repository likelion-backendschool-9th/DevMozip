import { describe, expect, it } from 'vitest';
import { addMonths, checkoutBlockReason, checkoutFailureCode, checkoutNotices, failureCodeForStatus, isCurrentSubscription } from './subscriptionView';
import { mockPremiumPlan } from '@/mocks/subscription';
import type { MySubscription, SubscriptionStatus } from '@/types/subscription';

const subscription = (status: SubscriptionStatus): MySubscription => ({
    status, premium: false, plan: mockPremiumPlan, startsAt: null, currentPeriodEnd: null, nextBillingAt: null, cancelRequestedAt: null, endedAt: null, paymentMethod: null,
});

describe('다음 결제일 계산', () => {
    it('한 달 뒤 같은 날로 계산한다', () => {
        const next = addMonths(new Date(2026, 10, 9), 1);
        expect([next.getFullYear(), next.getMonth(), next.getDate()]).toEqual([2026, 11, 9]);
    });
    it('그날이 없는 달은 마지막 날로 맞춘다', () => {
        const feb = addMonths(new Date(2027, 0, 31), 1);
        expect([feb.getMonth(), feb.getDate()]).toEqual([1, 28]);
        const leap = addMonths(new Date(2028, 0, 31), 1);
        expect([leap.getMonth(), leap.getDate()]).toEqual([1, 29]);
    });
});

describe('결제 시작 가능 여부', () => {
    it('무료 회원만 결제를 시작할 수 있다', () => {
        expect(checkoutBlockReason('free', subscription('NONE'))).toBeNull();
        expect(checkoutBlockReason('free', subscription('EXPIRED'))).toBeNull();
        expect(checkoutBlockReason('free', subscription('CANCELED'))).toBeNull();
    });
    it('이용 중·해지 예약 중이면 이미 이용 중으로 막는다', () => {
        expect(checkoutBlockReason('premium', subscription('NONE'))).toBe('already-subscribed');
        expect(checkoutBlockReason('free', subscription('CANCEL_REQUESTED'))).toBe('already-subscribed');
        expect(checkoutBlockReason('free', subscription('RENEWING'))).toBe('already-subscribed');
    });
    it('관리자와 비로그인은 결제하지 않는다', () => {
        expect(checkoutBlockReason('admin', subscription('NONE'))).toBe('not-allowed');
        expect(checkoutBlockReason(undefined, null)).toBe('login');
    });
    it('이용 기간 안의 상태를 구분한다', () => {
        expect((['ACTIVE', 'RENEWING', 'CANCEL_REQUESTED'] as const).every(isCurrentSubscription)).toBe(true);
        expect((['NONE', 'EXPIRED', 'CANCELED'] as const).some(isCurrentSubscription)).toBe(false);
    });
});

describe('결제 결과 코드와 고지', () => {
    it('API 상태 코드를 화면 결과 코드로 바꾼다', () => {
        expect([400, 401, 403, 404, 409, 500, 502, 503].map(failureCodeForStatus))
            .toEqual(['failed', 'login', 'not-allowed', 'expired', 'already-subscribed', 'failed', 'unknown', 'unavailable']);
    });
    it('정해진 코드만 받는다', () => {
        expect(checkoutFailureCode('cancelled')).toBe('cancelled');
        expect(checkoutFailureCode('<b>가짜</b>')).toBeNull();
        expect(checkoutFailureCode('toString')).toBeNull();
        expect(checkoutFailureCode(null)).toBeNull();
    });
    it('결제 전 고지에 금액·다음 결제일·해지·환불·실패 정책을 담는다', () => {
        const text = checkoutNotices(mockPremiumPlan, new Date(2026, 10, 9)).join(' ');
        expect(text).toContain('₩9,900');
        expect(text).toContain('2026년 12월 9일');
        expect(text).toContain('같은 날 0시(한국 시간)');
        expect(text).toContain('언제든 해지');
        expect(text).toContain('환불되지 않으며');
        expect(text).toContain('구독이 바로 취소');
    });
});
