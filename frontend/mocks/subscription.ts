import type { PaymentMethodSummary, SubscriptionPlan } from '@/types/subscription';

// 1단계(Mock) 화면에서 쓰는 원본 데이터. 백엔드 연동 후에는 상품과 구독 상태를 API에서 받는다.

/** 프리미엄 월 구독 상품. 가격은 확정 값(월 ₩9,900)이다 */
export const mockPremiumPlan: SubscriptionPlan = {
    id: 1,
    name: '프리미엄',
    price: 9900,
    currency: 'KRW',
    billingCycle: 'MONTHLY',
    featureLimits: { aiRecommendationsPerMonth: 100 },
};

/** Mock 결제에서 결제 수단별로 보여 줄 표시 정보 */
export const mockPaymentMethods: Record<PaymentMethodSummary['provider'], PaymentMethodSummary> = {
    TOSS_PAYMENTS: { provider: 'TOSS_PAYMENTS', display: '테스트카드 1234-****-****-5678' },
    KAKAO_PAY: { provider: 'KAKAO_PAY', display: '카카오페이' },
};
