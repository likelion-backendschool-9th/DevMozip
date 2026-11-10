import { beforeEach, describe, expect, it, vi } from 'vitest';
const doubles = vi.hoisted(() => ({ fetch: vi.fn(), set: vi.fn(), get: vi.fn(), refresh: vi.fn() }));
vi.mock('@/lib/auth/backend', () => ({ backendFetch: doubles.fetch }));
vi.mock('@/lib/auth/refresh', () => ({ refreshAccessToken: doubles.refresh }));
vi.mock('next/headers', () => ({ cookies: async () => ({ set: doubles.set, get: doubles.get }) }));
import { GET as callback } from './callback/route';
import { POST as confirm } from './confirm/route';
import { POST as checkout } from './checkout/route';
import { GET as history } from './me/route';
import { ACCESS_COOKIE, REFRESH_COOKIE } from '@/lib/auth/cookies';
import { isProtectedPath } from '@/lib/auth/paths';

const ORIGIN = 'http://localhost:3000';
const premiumAccess = `h.${Buffer.from(JSON.stringify({ sub: 'payer@devmozip.test', role: 'PREMIUM', exp: 9999999999 })).toString('base64url')}.s`;
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
// standalone 서버의 request.url은 localhost지만 브라우저 Origin과 Host는 실제 도메인이다.
const post = (path: string, body: unknown, origin = 'https://devmozip.example', headers: Record<string, string> = { host: 'devmozip.example' }) =>
    new Request(`${ORIGIN}${path}`, { method: 'POST', headers: { origin, 'content-type': 'application/json', ...headers }, body: JSON.stringify(body) });
const locationOf = (response: Response) => { expect(response.status).toBe(302); return response.headers.get('location'); };
const backendCall = () => doubles.fetch.mock.calls[0];

beforeEach(() => {
    vi.clearAllMocks();
    doubles.get.mockImplementation((name: string) => (name === REFRESH_COOKIE ? { value: 'refresh-token' } : undefined));
    doubles.refresh.mockResolvedValue(premiumAccess);
});

describe('/api/payments/confirm (PC)', () => {
    it('승인에 성공하면 토큰을 재발급해 PREMIUM 역할을 쿠키에 쓴다', async () => {
        doubles.fetch.mockResolvedValue(json(200, { status: 'ACTIVE' }));
        const response = await confirm(post('/api/payments/confirm', { orderId: 'order-1', billingKey: 'billing-key-1' }));
        expect(response.status).toBe(200);
        expect(backendCall()[0]).toBe('/api/v1/payments/confirm');
        expect(JSON.parse(backendCall()[1].body)).toEqual({ orderId: 'order-1', billingKey: 'billing-key-1' });
        expect(doubles.refresh).toHaveBeenCalledWith('refresh-token');
        expect(doubles.set).toHaveBeenCalledWith(ACCESS_COOKIE, premiumAccess, expect.objectContaining({ httpOnly: true, sameSite: 'lax' }));
    });
    it('승인이 거절되면 토큰을 재발급하지 않고 결과 코드를 돌려준다', async () => {
        doubles.fetch.mockResolvedValue(json(409, { message: '이미 프리미엄을 이용 중이거나 해지 예약 중입니다.' }));
        const response = await confirm(post('/api/payments/confirm', { orderId: 'order-1', billingKey: 'billing-key-1' }));
        expect(response.status).toBe(409);
        expect(await response.json()).toMatchObject({ code: 'already-subscribed' });
        expect(doubles.refresh).not.toHaveBeenCalled();
    });
    it('응답을 받지 못하면 결과를 모른다고 안내한다', async () => {
        doubles.fetch.mockRejectedValue(new Error('timeout'));
        const response = await confirm(post('/api/payments/confirm', { orderId: 'order-1', billingKey: 'billing-key-1' }));
        expect(response.status).toBe(502);
        expect(await response.json()).toMatchObject({ code: 'unknown' });
    });
    it('다른 사이트의 요청이나 형식이 틀린 값은 백엔드로 보내지 않는다', async () => {
        expect((await confirm(post('/api/payments/confirm', { orderId: 'order-1', billingKey: 'k' }, 'https://evil.test'))).status).toBe(403);
        expect((await confirm(post('/api/payments/confirm', { orderId: 'order-1', billingKey: 'k' }, 'https://devmozip.example', {}))).status).toBe(403);
        expect((await confirm(post('/api/payments/confirm', { orderId: 'order-1', billingKey: 'k' }, 'null'))).status).toBe(403);
        expect((await confirm(post('/api/payments/confirm', { orderId: '../x', billingKey: 'k' }))).status).toBe(400);
        expect((await confirm(post('/api/payments/confirm', { orderId: 'order-1', billingKey: '<script>' }))).status).toBe(400);
        expect(doubles.fetch).not.toHaveBeenCalled();
    });
});

describe('/api/payments/callback (모바일 결과 주소)', () => {
    it('빌링키로 승인하고 구독 관리로 이동한다', async () => {
        doubles.fetch.mockResolvedValue(json(200, { status: 'ACTIVE' }));
        expect(locationOf(await callback(new Request(`${ORIGIN}/api/payments/callback?orderId=order-1&billingKey=billing-key-1`))))
            .toBe('/subscription/manage?payment=success');
        expect(doubles.set).toHaveBeenCalledWith(ACCESS_COOKIE, premiumAccess, expect.anything());
    });
    it('결제창 취소·실패는 승인하지 않고 결제 페이지로 돌아간다', async () => {
        expect(locationOf(await callback(new Request(`${ORIGIN}/api/payments/callback?orderId=order-1&code=FAILURE_TYPE_PG&pgCode=PAY_PROCESS_CANCELED`))))
            .toBe('/subscription/checkout?payment=cancelled');
        expect(locationOf(await callback(new Request(`${ORIGIN}/api/payments/callback?orderId=order-1&code=FAILURE_TYPE_PG&message=${encodeURIComponent('한도 초과')}`))))
            .toBe('/subscription/checkout?payment=failed');
        expect(locationOf(await callback(new Request(`${ORIGIN}/api/payments/callback?orderId=order-1`)))).toBe('/subscription/checkout?payment=failed');
        expect(doubles.fetch).not.toHaveBeenCalled();
    });
    it('승인이 거절되면 결과 코드만 싣고 이동한다', async () => {
        doubles.fetch.mockResolvedValue(json(404, { message: '<b>주입</b>' }));
        expect(locationOf(await callback(new Request(`${ORIGIN}/api/payments/callback?orderId=order-1&billingKey=billing-key-1`))))
            .toBe('/subscription/checkout?payment=expired');
    });
    it('결과 주소는 보호 경로가 아니며 로그인 확인은 백엔드가 한다', () => {
        expect(isProtectedPath('/api/payments/callback')).toBe(false);
    });
});

describe('/api/payments/checkout, /api/payments/me', () => {
    it('nginx 뒤에서는 X-Forwarded-Host를 Origin과 비교한다', async () => {
        doubles.fetch.mockResolvedValue(json(201, { orderId: 'order-1' }));
        const forwarded = { host: 'frontend-blue:3000', 'x-forwarded-host': 'devmozip.example' };
        expect((await checkout(post('/api/payments/checkout', { planId: 1, provider: 'KAKAO_PAY' }, 'https://devmozip.example', forwarded))).status).toBe(201);
        expect((await checkout(post('/api/payments/checkout', { planId: 1, provider: 'KAKAO_PAY' }, 'https://frontend-blue:3000', forwarded))).status).toBe(403);
    });
    it('상품과 결제 수단만 백엔드로 넘기고 금액은 버린다', async () => {
        doubles.fetch.mockResolvedValue(json(201, { orderId: 'order-1' }));
        const response = await checkout(post('/api/payments/checkout', { planId: 1, provider: 'TOSS_PAYMENTS', amount: 1, userId: 99 }));
        expect(response.status).toBe(201);
        expect(JSON.parse(backendCall()[1].body)).toEqual({ planId: 1, provider: 'TOSS_PAYMENTS' });
    });
    it('잘못된 상품·결제 수단은 400이다', async () => {
        expect((await checkout(post('/api/payments/checkout', { planId: '1', provider: 'TOSS_PAYMENTS' }))).status).toBe(400);
        expect((await checkout(post('/api/payments/checkout', { planId: 1, provider: 'PAYPAL' }))).status).toBe(400);
        expect(doubles.fetch).not.toHaveBeenCalled();
    });
    it('결제 내역 페이지 범위를 확인하고 그대로 전달한다', async () => {
        doubles.fetch.mockResolvedValue(json(200, { items: [], page: 1, size: 20, hasNext: false }));
        expect((await history(new Request(`${ORIGIN}/api/payments/me?page=1&size=20`))).status).toBe(200);
        expect(backendCall()[0]).toBe('/api/v1/payments/me?page=1&size=20');
        expect((await history(new Request(`${ORIGIN}/api/payments/me?size=101`))).status).toBe(400);
    });
});
