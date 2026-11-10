import { CANCEL_TIMEOUT, forward, rejectCrossSite } from '@/lib/subscription/server';

// 13.4 구독 해지. 요청 본문은 쓰지 않는다.
export async function POST(request: Request) {
    const rejected = rejectCrossSite(request);
    if (rejected) return rejected;
    return forward('/api/v1/subscription/cancel', { method: 'POST', signal: AbortSignal.timeout(CANCEL_TIMEOUT) });
}
