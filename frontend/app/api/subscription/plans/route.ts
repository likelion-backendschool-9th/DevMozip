import { forward } from '@/lib/subscription/server';

// 13.1 상품 안내. 비로그인도 볼 수 있다.
export async function GET() {
    return forward('/api/v1/subscription/plans');
}
