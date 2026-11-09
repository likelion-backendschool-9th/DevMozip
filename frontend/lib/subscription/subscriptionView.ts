import type { CheckoutFailureCode, MySubscription, PaymentProvider, PaymentStatus, SubscriptionPlan, SubscriptionStatus } from '@/types/subscription';

// 구독·결제 화면이 함께 쓰는 문구와 계산. 상태 저장은 하지 않는다.

export const PROVIDER_LABELS: Record<PaymentProvider, string> = {
    TOSS_PAYMENTS: '토스페이먼츠 카드',
    KAKAO_PAY: '카카오페이',
};

export const PROVIDER_OPTIONS: { provider: PaymentProvider; title: string; description: string }[] = [
    { provider: 'TOSS_PAYMENTS', title: '토스페이먼츠 카드 자동결제', description: '카드를 한 번 등록하면 매월 같은 카드로 결제됩니다.' },
    { provider: 'KAKAO_PAY', title: '카카오페이 정기결제', description: '카카오페이에 등록한 결제 수단으로 매월 결제됩니다.' },
];

/**
 * 결제 수단을 골랐을 때 보여 주는 테스트 결제 안내. 지금은 포트원 테스트 채널만 쓴다.
 * 라이브 채널로 바꿀 때 이 안내를 지운다(docs/premium-payment-plan.md 8단계).
 */
export const TEST_PAYMENT_NOTICES: Partial<Record<PaymentProvider, string>> = {
    TOSS_PAYMENTS: '테스트 결제 환경에서는 본인인증 문자가 발송되지 않습니다. 결제창에서 인증번호를 묻으면 000000을 입력해 주세요.',
};

export const STATUS_LABELS: Record<SubscriptionStatus, string> = {
    NONE: '무료 요금제',
    ACTIVE: '프리미엄 이용 중',
    RENEWING: '이번 달 결제 처리 중',
    CANCEL_REQUESTED: '해지 예약',
    EXPIRED: '해지 후 이용 종료',
    CANCELED: '결제 실패로 구독 취소',
};

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
    PENDING: '처리 중',
    PAID: '결제 완료',
    FAILED: '결제 실패',
    CANCELED: '결제 취소',
};

/** 결제 결과 코드별 안내. URL에는 코드만 싣고 문구는 여기서 정한다 */
export const CHECKOUT_FAILURE_MESSAGES: Record<CheckoutFailureCode, string> = {
    cancelled: '결제를 취소했습니다. 다시 결제하려면 결제 수단을 선택해 주세요.',
    failed: '결제가 승인되지 않았습니다. 결제 수단을 확인한 뒤 다시 시도해 주세요.',
    unknown: '결제 결과를 확인하지 못했습니다. 결제 내역을 확인한 뒤 다시 시도해 주세요.',
    unavailable: '지금은 결제를 진행할 수 없습니다. 잠시 후 다시 시도해 주세요.',
    'already-subscribed': '이미 프리미엄을 이용 중이거나 해지 예약 중입니다. 구독 관리에서 상태를 확인해 주세요.',
    'not-allowed': '이 계정은 구독 결제 대상이 아닙니다.',
    expired: '결제 시간이 지났습니다. 처음부터 다시 결제해 주세요.',
    login: '로그인이 만료되었습니다. 다시 로그인한 뒤 결제해 주세요.',
};

export function checkoutFailureCode(value: string | null): CheckoutFailureCode | null {
    return value && Object.hasOwn(CHECKOUT_FAILURE_MESSAGES, value) ? value as CheckoutFailureCode : null;
}

/** 결제 준비·승인 API의 HTTP 상태를 화면 결과 코드로 바꾼다 (docs/premium-payment-api.md 오류 응답) */
export function failureCodeForStatus(status: number): CheckoutFailureCode {
    if (status === 401) return 'login';
    if (status === 403) return 'not-allowed';
    if (status === 404) return 'expired';
    if (status === 409) return 'already-subscribed';
    if (status === 502) return 'unknown';
    if (status === 503) return 'unavailable';
    return 'failed';
}

/** 해지 예약까지 포함해 지금 구독 기간 안에 있는 상태 */
export function isCurrentSubscription(status: SubscriptionStatus) {
    return status === 'ACTIVE' || status === 'RENEWING' || status === 'CANCEL_REQUESTED';
}

export function formatWon(amount: number) {
    return `₩${amount.toLocaleString('ko-KR')}`;
}

export function formatDate(value: string | null) {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '-';
    return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', timeZone: 'Asia/Seoul' });
}

export function formatDateTime(value: string | null) {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '-';
    return date.toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Seoul' });
}

export function cycleLabel(plan: SubscriptionPlan) {
    return plan.billingCycle === 'MONTHLY' ? '매월' : plan.billingCycle;
}

/**
 * 한 달 뒤 같은 날. 그날이 없는 달은 그달 마지막 날이다(서버와 같은 규칙).
 * 결제 화면의 "다음 결제일" 안내에 쓴다.
 */
export function addMonths(from: Date, months: number) {
    const target = new Date(from.getTime());
    const day = target.getDate();
    target.setDate(1);
    target.setMonth(target.getMonth() + months);
    const lastDay = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate();
    target.setDate(Math.min(day, lastDay));
    return target;
}

/** 결제 전에 보여 주는 고지(docs/premium-payment-plan.md 3절 "결제 전 고지") */
export function checkoutNotices(plan: SubscriptionPlan, today: Date) {
    return [
        `오늘 ${formatWon(plan.price)}이 결제되고, 이후 ${cycleLabel(plan)} 같은 날 0시(한국 시간)에 자동으로 결제됩니다(다음 결제일 ${formatDate(addMonths(today, 1).toISOString())}). 그날이 없는 달은 마지막 날에 결제됩니다.`,
        '구독 관리 페이지에서 언제든 해지할 수 있습니다. 해지하면 다음 결제부터 청구되지 않고, 이미 결제한 기간이 끝날 때까지 프리미엄을 이용할 수 있습니다.',
        '이미 결제한 금액은 환불되지 않으며, 해지해도 남은 기간을 나눠 돌려주지 않습니다.',
        '매월 결제가 실패하면 구독이 바로 취소되고 등록한 결제 수단도 삭제됩니다. 다시 이용하려면 새로 결제해 주세요.',
    ];
}

/** 결제를 시작할 수 없는 이유. 시작할 수 있으면 null */
export function checkoutBlockReason(role: string | undefined, subscription: MySubscription | null): CheckoutFailureCode | null {
    if (!role) return 'login';
    if (role === 'premium' || (subscription && isCurrentSubscription(subscription.status))) return 'already-subscribed';
    if (role !== 'free') return 'not-allowed';
    return null;
}
