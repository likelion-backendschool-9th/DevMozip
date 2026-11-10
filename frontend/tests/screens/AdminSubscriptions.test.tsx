import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { PaymentsView, SubscriptionsView } from '@/app/admin/subscriptions/_components/ApiAdminSubscriptions';
import { formatMoney } from '@/lib/administration/adminBilling';
import type { AdminPayment, AdminSubscription } from '@/types/adminApi';

const noop = () => {};
const member = { id: 2, tag: 'kim', nickname: '김개발', withdrawn: false };
const counts = { ACTIVE: 1, RENEWING: 0, CANCEL_REQUESTED: 0, EXPIRED: 0, CANCELED: 1 };
const subscription: AdminSubscription = { id: 10, member, planName: '프리미엄 월간', status: 'ACTIVE', startsAt: '2026-11-01T00:00:00',
    currentPeriodEnd: '2026-12-01T00:00:00', cancelRequestedAt: null, endedAt: null };
const payment: AdminPayment = { id: 100, orderId: 'order-100', member, amount: 9900, currency: 'KRW', status: 'PAID', provider: 'TOSS_PAYMENTS',
    method: '신한카드 1234', paidAt: '2026-11-01T00:00:01', createdAt: '2026-11-01T00:00:00' };
const ready = <S, T>(summary: S, items: T[]) => ({ status: 'ready' as const, data: { summary, items, totalElements: items.length, page: 0, totalPages: 1 } });

describe('서버 구독·결제 화면 (14.12·14.13)', () => {
    it('구독 요약과 탈퇴 회원의 끝난 구독을 보여 준다', () => {
        const html = renderToStaticMarkup(<SubscriptionsView onRetry={noop} onPage={noop} load={ready({ statusCounts: counts, current: 1 }, [
            subscription,
            { ...subscription, id: 11, status: 'CANCELED', member: { id: 3, tag: null, nickname: '탈퇴회원', withdrawn: true }, endedAt: '2026-10-20T00:00:00', cancelRequestedAt: '2026-10-20T00:00:00' },
        ])} />);
        expect(html).toContain('이용 기간 안의 구독 1건');
        expect(html).toContain('취소 1');
        expect(html).toContain('href="/users/kim"');
        expect(html).toContain('<b>탈퇴회원 (탈퇴)</b>');
        expect(html).toContain('종료 2026-10-20 00:00');
    });
    it('결제 합계와 결제 수단 표시를 보여 준다', () => {
        const html = renderToStaticMarkup(<PaymentsView onRetry={noop} onPage={noop} load={ready({ paidTotals: [{ currency: 'KRW', amount: 19800 }] }, [payment,
            { ...payment, id: 101, status: 'FAILED', method: null, paidAt: null }])} />);
        expect(html).toContain('결제 완료 합계 ₩19,800');
        expect(html).toContain('결제 완료 · 신한카드 1234 · 주문 order-100');
        expect(html).toContain('결제 실패 · TOSS_PAYMENTS');
        expect(html).toContain('₩9,900');
        expect(renderToStaticMarkup(<PaymentsView onRetry={noop} onPage={noop} load={ready({ paidTotals: [] }, [])} />)).toContain('결제 완료 합계 ₩0');
        expect(formatMoney(10, 'USD')).toBe('10 USD');
    });
});
