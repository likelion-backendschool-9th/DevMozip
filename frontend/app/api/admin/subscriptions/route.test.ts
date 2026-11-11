import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
vi.mock('@/lib/auth/session', () => ({ getSession: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { getSession } from '@/lib/auth/session';
import { GET as subscriptions } from './route';
import { GET as payments } from '../payments/route';

const member = { id: 2, tag: 'kim', nickname: '김개발', withdrawn: false };
const counts = { ACTIVE: 1, RENEWING: 0, CANCEL_REQUESTED: 0, EXPIRED: 0, CANCELED: 1 };
const subscription = { id: 10, member, planName: '프리미엄 월간', status: 'ACTIVE', startsAt: '2026-11-01T00:00:00', currentPeriodEnd: '2026-12-01T00:00:00',
    cancelRequestedAt: null, endedAt: null };
const payment = { id: 100, orderId: 'order-100', member, amount: 9900, currency: 'KRW', status: 'PAID', provider: 'TOSS_PAYMENTS', method: '신한카드 1234',
    paidAt: '2026-11-01T00:00:01', createdAt: '2026-11-01T00:00:00' };
const page = (summary: unknown, items: unknown[]) => ({ summary, items, totalElements: items.length, page: 0, totalPages: 1 });
beforeEach(() => {
    vi.mocked(backendFetch).mockReset();
    vi.mocked(getSession).mockResolvedValue({ username: 'admin@devmozip.demo', role: 'ADMIN', isAdmin: true, token: 't' } as never);
});

describe('구독·결제 BFF (14.12·14.13)', () => {
    it('구독 상태 조건과 요약·목록 형식을 확인한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(page({ statusCounts: counts, current: 1 }, [subscription])));
        const body = await (await subscriptions(new Request('http://localhost/api/admin/subscriptions?status=ACTIVE'))).json();
        expect(body).toEqual(page({ statusCounts: counts, current: 1 }, [subscription]));
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/admin/subscriptions?status=ACTIVE', {});
        expect((await subscriptions(new Request('http://localhost/api/admin/subscriptions?status=PAUSED'))).status).toBe(400);
        vi.mocked(backendFetch).mockResolvedValue(Response.json(page({ statusCounts: { ACTIVE: 1 }, current: 1 }, [])));
        expect((await subscriptions(new Request('http://localhost/api/admin/subscriptions'))).status).toBe(502);
    });
    it('결제 목록에서 허용한 필드만 넘기고 빌링키 같은 값은 버린다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(page({ paidTotals: [{ currency: 'KRW', amount: 19800 }] },
            [{ ...payment, billingKey: 'secret', providerPaymentId: 'pg-1' }])));
        const response = await payments(new Request('http://localhost/api/admin/payments?status=PAID'));
        const text = await response.text();
        expect(JSON.parse(text)).toEqual(page({ paidTotals: [{ currency: 'KRW', amount: 19800 }] }, [payment]));
        expect(text).not.toContain('secret');
        expect((await payments(new Request('http://localhost/api/admin/payments?status=REFUNDED'))).status).toBe(400);
    });
});
