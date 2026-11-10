import { forward } from '@/lib/subscription/server';

// 13.3 구독 상태
export async function GET() {
    return forward('/api/v1/subscription/me');
}
