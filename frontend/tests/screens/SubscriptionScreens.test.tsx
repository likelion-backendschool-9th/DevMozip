import { beforeEach, describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import PremiumPage from '@/app/subscription/page';
import { AppLayout } from '@/app/_components/AppLayout';
import { SubscriptionTabs } from '@/app/subscription/_components/SubscriptionTabs';
import { resetLocalStores } from '@/lib/localStore';
import { fixture } from '@/tests/helpers/RouterFixture';
import { signIn } from '@/tests/helpers/session';
import { CheckoutForm } from '@/app/subscription/checkout/_components/CheckoutForm';
import { SubscriptionStatusPanel } from '@/app/subscription/manage/_components/SubscriptionStatusPanel';
import { PaymentHistoryList } from '@/app/subscription/history/_components/PaymentHistoryList';
import { PremiumAction } from '@/app/subscription/_components/PremiumAction';
import { PaymentInfoView } from '@/app/(user)/settings/privacy/_components/PaymentInfoPanel';
import { isProtectedPath } from '@/lib/auth/paths';
import { mockPremiumPlan } from '@/mocks/subscription';
import type { MySubscription, PaymentHistoryItem, SubscriptionStatus } from '@/types/subscription';

const noop = () => {};
const today = new Date(2026, 10, 9);
const render = (url: string, element: React.ReactNode) => { fixture.url = url; return renderToStaticMarkup(element); };
beforeEach(() => resetLocalStores());
const checkout = (props: Partial<Parameters<typeof CheckoutForm>[0]> = {}) => renderToStaticMarkup(
    <CheckoutForm plan={mockPremiumPlan} provider={null} agreed={false} busy={false} blockReason={null} failure={null} today={today}
        onProvider={noop} onAgreed={noop} onSubmit={noop} {...props} />,
);
const subscription = (status: SubscriptionStatus, extra: Partial<MySubscription> = {}): MySubscription => ({
    status, premium: status === 'ACTIVE' || status === 'CANCEL_REQUESTED' || status === 'RENEWING', plan: mockPremiumPlan,
    startsAt: '2026-11-09T10:00:00', currentPeriodEnd: '2026-12-09T10:00:00', nextBillingAt: status === 'ACTIVE' ? '2026-12-09T10:00:00' : null,
    cancelRequestedAt: null, endedAt: null, paymentMethod: { provider: 'TOSS_PAYMENTS', display: '신한카드 1234****' }, ...extra,
});
const status = (value: MySubscription, paid = false) => renderToStaticMarkup(<SubscriptionStatusPanel subscription={value} paid={paid} busy={false} error="" onCancel={noop} />);
const payment = (id: number, extra: Partial<PaymentHistoryItem> = {}): PaymentHistoryItem => ({
    id, occurredAt: '2026-11-09T10:00:00', planName: '프리미엄', provider: 'KAKAO_PAY', paymentMethod: '카카오페이', amount: 9900, currency: 'KRW', status: 'PAID', ...extra,
});

describe('/subscription 상품 안내', () => {
    it('로그인·역할에 따라 버튼을 바꾼다', () => {
        expect(renderToStaticMarkup(<PremiumAction role={undefined} />)).toContain('href="/login?next=%2Fsubscription%2Fcheckout"');
        expect(renderToStaticMarkup(<PremiumAction role="free" />)).toContain('href="/subscription/checkout"');
        expect(renderToStaticMarkup(<PremiumAction role="premium" />)).toContain('href="/subscription/manage"');
        expect(renderToStaticMarkup(<PremiumAction role="admin" />)).toContain('구독 결제 대상이 아닙니다');
    });
    it('상품 안내에는 탭이 없고, 구독 관리·결제 내역 탭에는 상품 안내가 없다', () => {
        signIn('demo-user');
        expect(render('/subscription', <PremiumPage />)).not.toContain('aria-label="프리미엄 메뉴"');
        const tabs = renderToStaticMarkup(<SubscriptionTabs current="/subscription/manage" />);
        expect(tabs).toContain('href="/subscription/manage"');
        expect(tabs).toContain('href="/subscription/history"');
        expect(tabs).not.toContain('상품 안내');
        expect(tabs).toContain('aria-current="page"');
    });
    it('헤더의 개발자 탐색 오른쪽에 프리미엄 메뉴가 있고 하위 페이지에서도 강조된다', () => {
        const header = (url: string) => render(url, <AppLayout />).match(/<nav aria-label="주 내비게이션">(.*?)<\/nav>/)?.[1] ?? '';
        const nav = header('/subscription');
        expect([...nav.matchAll(/>([^<]+)<\/a>/g)].map(match => match[1])).toEqual(['프로젝트 모집', 'SNS', '개발자 탐색', '프리미엄']);
        expect(nav).toMatch(/aria-current="page"[^>]*href="\/subscription"|href="\/subscription"[^>]*aria-current="page"/);
        expect(header('/subscription/manage')).toMatch(/href="\/subscription"[^>]*aria-current="page"|aria-current="page"[^>]*href="\/subscription"/);
        expect(header('/projects')).not.toMatch(/href="\/subscription"[^>]*aria-current="page"|aria-current="page"[^>]*href="\/subscription"/);
    });
    it('결제·관리·내역만 로그인 보호 경로다', () => {
        expect(isProtectedPath('/subscription')).toBe(false);
        expect(['/subscription/checkout', '/subscription/manage', '/subscription/history'].every(isProtectedPath)).toBe(true);
    });
});

describe('/subscription/checkout 결제 화면', () => {
    it('두 결제 수단과 결제 전 고지를 보여 주고 동의 전에는 결제 버튼을 막는다', () => {
        const html = checkout();
        expect(html).toContain('토스페이먼츠 카드 자동결제');
        expect(html).toContain('카카오페이 정기결제');
        expect(html).toContain('환불되지 않으며');
        expect(html).toMatch(/<button[^>]*type="submit"[^>]*disabled=""/);
        expect(html).not.toContain('데모');
    });
    it('결제 수단을 고르고 동의해야 결제 버튼이 열린다', () => {
        expect(checkout({ provider: 'KAKAO_PAY' })).toMatch(/<button[^>]*type="submit"[^>]*disabled=""/);
        expect(checkout({ agreed: true })).toMatch(/<button[^>]*type="submit"[^>]*disabled=""/);
        expect(checkout({ provider: 'KAKAO_PAY', agreed: true })).not.toMatch(/<button[^>]*type="submit"[^>]*disabled=""/);
    });
    it('토스페이먼츠를 고르면 테스트 인증번호(000000) 안내를 보여 주고, 다른 경우에는 보여 주지 않는다', () => {
        expect(checkout({ provider: 'TOSS_PAYMENTS' })).toContain('000000');
        expect(checkout({ provider: 'TOSS_PAYMENTS' })).toContain('본인인증 문자가 발송되지 않습니다');
        expect(checkout({ provider: 'KAKAO_PAY' })).not.toContain('000000');
        expect(checkout()).not.toContain('000000');
    });
    it('결과 코드에 맞는 안내만 보여 준다', () => {
        expect(checkout({ failure: 'cancelled' })).toContain('결제를 취소했습니다.');
        expect(checkout({ failure: 'failed' })).toContain('결제가 승인되지 않았습니다.');
    });
    it('결제할 수 없는 계정은 폼 대신 이유와 이동 버튼을 보여 준다', () => {
        const html = checkout({ blockReason: 'already-subscribed' });
        expect(html).not.toContain('<form');
        expect(html).toContain('href="/subscription/manage"');
        expect(checkout({ blockReason: 'not-allowed' })).toContain('href="/subscription"');
    });
});

describe('/subscription/manage 구독 상태', () => {
    it('이용 중이면 다음 결제일·결제 수단·해지 버튼을 보여 준다', () => {
        const html = status(subscription('ACTIVE'), true);
        expect(html).toContain('결제를 완료했습니다.');
        expect(html).toContain('프리미엄 이용 중');
        expect(html).toContain('다음 결제일');
        expect(html).toContain('신한카드 1234****');
        expect(html).toContain('구독 해지');
    });
    it('해지 예약이면 이용 종료일을 보여 주고 해지 버튼을 숨긴다', () => {
        const html = status(subscription('CANCEL_REQUESTED', { paymentMethod: null }));
        expect(html).toContain('해지 예약');
        expect(html).toContain('이용 종료일');
        expect(html).toContain('등록된 결제 수단 없음');
        expect(html).not.toContain('구독 해지');
    });
    it('결제 실패로 취소됐으면 안내와 새 결제 버튼을 보여 준다', () => {
        const html = status(subscription('CANCELED', { premium: false, endedAt: '2026-12-09T10:00:00' }));
        expect(html).toContain('매월 결제가 실패해 구독이 취소되었습니다.');
        expect(html).toContain('href="/subscription/checkout"');
    });
    it('결제 기록 없이 프리미엄인 계정은 해지 버튼 없이 이용 중으로 보여 준다', () => {
        const html = status(subscription('NONE', { premium: true, plan: null, paymentMethod: null }));
        expect(html).toContain('결제 기록 없이 프리미엄 권한이 부여된 계정입니다.');
        expect(html).not.toContain('구독 해지');
        expect(html).not.toContain('프리미엄 시작하기');
    });
    it('무료 회원은 시작 버튼을 본다', () => {
        expect(status(subscription('NONE', { premium: false, plan: null }))).toContain('지금은 무료 요금제를 이용하고 있습니다.');
    });
});

describe('결제 내역과 개인정보 설정의 결제 정보', () => {
    it('결제 내역의 상태·금액·결제 수단을 보여 준다', () => {
        const html = renderToStaticMarkup(<PaymentHistoryList items={[payment(1), payment(2, { status: 'FAILED', paymentMethod: '신한카드 1234****' })]} />);
        expect(html).toContain('결제 완료');
        expect(html).toContain('결제 실패');
        expect(html).toContain('₩9,900');
        expect(html).toContain('신한카드 1234****');
        expect(renderToStaticMarkup(<PaymentHistoryList items={[]} />)).toContain('결제 내역이 없습니다.');
    });
    it('개인정보 설정은 같은 구독 상태와 최근 3건을 보여 준다', () => {
        const html = renderToStaticMarkup(<PaymentInfoView role="premium" subscription={subscription('ACTIVE')} paymentMethod={{ provider: 'KAKAO_PAY', display: '카카오페이' }}
            payments={[payment(1), payment(2), payment(3), payment(4, { amount: 1234 })]} />);
        expect(html).toContain('프리미엄 이용 중');
        expect(html).toContain('카카오페이');
        expect(html).not.toContain('₩1,234');
        expect(html).not.toContain('데모');
        expect(renderToStaticMarkup(<PaymentInfoView role="admin" subscription={subscription('NONE', { premium: false })} paymentMethod={null} payments={[]} />)).toContain('관리자');
    });
});
