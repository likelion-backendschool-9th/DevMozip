import type { CheckoutStart } from '@/types/subscription';

// 포트원 V2 브라우저 SDK. 결제 페이지에서만 스크립트로 불러온다(npm 의존성 없음).
export const PORTONE_SDK_URL = 'https://cdn.portone.io/v2/browser-sdk.js';

/** requestIssueBillingKey 결과. PC(IFRAME)는 결과를 돌려주고, 모바일(REDIRECTION)은 결과 주소로 이동해 돌아오지 않는다 */
interface IssueBillingKeyResponse {
    billingKey?: string;
    code?: string;
    message?: string;
    pgCode?: string;
}
interface PortOneSdk {
    requestIssueBillingKey: (request: Record<string, unknown>) => Promise<IssueBillingKeyResponse | undefined>;
}
declare global {
    interface Window { PortOne?: PortOneSdk }
}

let loading: Promise<PortOneSdk> | null = null;

export function loadPortOne(): Promise<PortOneSdk> {
    if (window.PortOne) return Promise.resolve(window.PortOne);
    loading ??= new Promise<PortOneSdk>((resolve, reject) => {
        const script = document.createElement('script');
        script.src = PORTONE_SDK_URL;
        script.async = true;
        script.onload = () => (window.PortOne ? resolve(window.PortOne) : reject(new Error('portone-sdk-missing')));
        script.onerror = () => { loading = null; reject(new Error('portone-sdk-load-failed')); };
        document.head.appendChild(script);
    });
    return loading;
}

export type BillingKeyResult = { status: 'issued'; billingKey: string } | { status: 'cancelled' } | { status: 'failed' } | { status: 'redirecting' };

/** 사용자가 결제창을 닫거나 취소했는지. 결제대행사마다 코드가 달라 코드·메시지에서 취소 표시를 찾는다 */
export function isCancelled(response: { code?: string; message?: string; pgCode?: string }) {
    return /CANCEL/i.test(`${response.code ?? ''} ${response.pgCode ?? ''}`) || /취소/.test(response.message ?? '');
}

/** 결제 준비 응답으로 카드 등록창(토스페이먼츠) 또는 결제창(카카오페이)을 연다 */
export async function requestBillingKey(start: CheckoutStart, sdk?: PortOneSdk): Promise<BillingKeyResult> {
    const portOne = sdk ?? await loadPortOne();
    const response = await portOne.requestIssueBillingKey({
        storeId: start.storeId,
        channelKey: start.channelKey,
        billingKeyMethod: start.billingKeyMethod,
        // 서버가 이 값으로 빌링키가 이 주문에서 발급됐는지 확인한다.
        issueId: start.orderId,
        issueName: start.issueName,
        customer: { customerId: start.customerId },
        redirectUrl: start.redirectUrl,
    });
    if (!response) return { status: 'redirecting' };
    if (response.code !== undefined) return isCancelled(response) ? { status: 'cancelled' } : { status: 'failed' };
    return response.billingKey ? { status: 'issued', billingKey: response.billingKey } : { status: 'failed' };
}
