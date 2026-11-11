// 14.12 구독 관리·14.13 결제 관리의 응답 해석 (docs/admin-plan.md 5.10). 조회만 한다.
import { fields, parseAdminPage, type QueryRule } from './adminApi';
import { parseAdminParty } from './adminReports';
import type {
    AdminPayment, AdminPaymentStatus, AdminPaymentSummary, AdminSubscription, AdminSubscriptionStatus, AdminSubscriptionSummary, AdminSummaryPage,
} from '@/types/adminApi';

export const subscriptionStatuses: readonly AdminSubscriptionStatus[] = ['ACTIVE', 'RENEWING', 'CANCEL_REQUESTED', 'EXPIRED', 'CANCELED'];
export const paymentStatuses: readonly AdminPaymentStatus[] = ['PENDING', 'PAID', 'FAILED', 'CANCELED'];
export const subscriptionStatusLabels: Record<AdminSubscriptionStatus, string> = {
    ACTIVE: '이용 중', RENEWING: '정기결제 처리 중', CANCEL_REQUESTED: '해지 예약', EXPIRED: '기간 종료', CANCELED: '취소',
};
export const paymentStatusLabels: Record<AdminPaymentStatus, string> = { PENDING: '처리 중', PAID: '결제 완료', FAILED: '결제 실패', CANCELED: '결제 취소' };

export const subscriptionQueryRules: Record<string, QueryRule> = { status: subscriptionStatuses };
export const paymentQueryRules: Record<string, QueryRule> = { status: paymentStatuses };

export function parseSubscriptionPage(value: unknown): AdminSummaryPage<AdminSubscriptionSummary, AdminSubscription> {
    const s = fields(fields(value, 'subscriptions').obj('summary'), 'summary');
    const counts = fields(s.obj('statusCounts'), 'statusCounts');
    const summary: AdminSubscriptionSummary = {
        statusCounts: Object.fromEntries(subscriptionStatuses.map(status => [status, counts.num(status)])) as Record<AdminSubscriptionStatus, number>,
        current: s.num('current'),
    };
    return {
        summary, ...parseAdminPage(value, raw => {
            const f = fields(raw, 'subscription');
            return {
                id: f.num('id'), member: parseAdminParty(f.obj('member')), planName: f.str('planName'), status: f.oneOf('status', subscriptionStatuses),
                startsAt: f.str('startsAt'), currentPeriodEnd: f.str('currentPeriodEnd'), cancelRequestedAt: f.optStr('cancelRequestedAt'), endedAt: f.optStr('endedAt'),
            };
        }),
    };
}

export function parsePaymentPage(value: unknown): AdminSummaryPage<AdminPaymentSummary, AdminPayment> {
    const s = fields(fields(value, 'payments').obj('summary'), 'summary');
    const summary: AdminPaymentSummary = {
        paidTotals: s.list('paidTotals').map(raw => { const t = fields(raw, 'total'); return { currency: t.str('currency'), amount: t.num('amount') }; }),
    };
    return {
        summary, ...parseAdminPage(value, raw => {
            const f = fields(raw, 'payment');
            return {
                id: f.num('id'), orderId: f.str('orderId'), member: parseAdminParty(f.obj('member')), amount: f.num('amount'), currency: f.str('currency'),
                status: f.oneOf('status', paymentStatuses), provider: f.str('provider'), method: f.optStr('method'), paidAt: f.optStr('paidAt'),
                createdAt: f.str('createdAt'),
            };
        }),
    };
}

/** 금액 표시. 원화는 ₩ 기호를, 그 밖은 통화 코드를 붙인다. */
export const formatMoney = (amount: number, currency: string) =>
    currency === 'KRW' ? `₩${Math.round(amount).toLocaleString('ko-KR')}` : `${amount.toLocaleString('ko-KR')} ${currency}`;
