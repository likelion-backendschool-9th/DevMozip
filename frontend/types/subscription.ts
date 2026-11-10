// 프리미엄 구독·결제 화면이 쓰는 형태. 백엔드 API(docs/premium-payment-plan.md 5절) 응답과 같은 모양이다.

/** 포트원 채널로 연결한 결제 수단 */
export type PaymentProvider = 'TOSS_PAYMENTS' | 'KAKAO_PAY';

/** 구독한 적이 없으면 NONE, 그 밖에는 가장 최근 구독의 상태 */
export type SubscriptionStatus = 'NONE' | 'ACTIVE' | 'RENEWING' | 'CANCEL_REQUESTED' | 'EXPIRED' | 'CANCELED';

export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'CANCELED';

export interface SubscriptionPlan {
    id: number;
    name: string;
    price: number;
    currency: string;
    billingCycle: string;
    featureLimits: Record<string, number>;
}

export interface PaymentMethodSummary {
    provider: PaymentProvider;
    display: string;
}

export interface MySubscription {
    status: SubscriptionStatus;
    /** 현재 역할이 PREMIUM인지. 결제 기록 없이 PREMIUM인 계정도 true다 */
    premium: boolean;
    plan: SubscriptionPlan | null;
    startsAt: string | null;
    currentPeriodEnd: string | null;
    nextBillingAt: string | null;
    cancelRequestedAt: string | null;
    endedAt: string | null;
    paymentMethod: PaymentMethodSummary | null;
}

export interface PaymentHistoryItem {
    id: number;
    /** PAID는 결제 완료 시각, 그 밖에는 결제를 시도한 시각 */
    occurredAt: string;
    planName: string;
    provider: PaymentProvider;
    paymentMethod: string;
    amount: number;
    currency: string;
    status: PaymentStatus;
}

export interface PaymentHistoryPage {
    items: PaymentHistoryItem[];
    page: number;
    size: number;
    hasNext: boolean;
}

/** 결제 결과. failed의 code는 화면 문구를 고르는 값이며 URL에도 이 값만 싣는다 */
export type CheckoutResult = { status: 'success' } | { status: 'redirecting' } | { status: 'failed'; code: CheckoutFailureCode; message?: string };

export type CheckoutFailureCode = 'cancelled' | 'failed' | 'unknown' | 'unavailable' | 'already-subscribed' | 'not-allowed' | 'expired' | 'login';

/** 결제 준비 응답. 포트원 브라우저 SDK requestIssueBillingKey()에 넘긴다 */
export interface CheckoutStart {
    orderId: string;
    provider: PaymentProvider;
    storeId: string;
    channelKey: string;
    billingKeyMethod: 'CARD' | 'EASY_PAY';
    issueName: string;
    customerId: string;
    redirectUrl: string;
}
