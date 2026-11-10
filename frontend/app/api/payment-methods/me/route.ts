import { forward } from '@/lib/subscription/server';

// 17.5 결제 수단. 등록된 결제 수단이 없으면 204
export async function GET() {
    return forward('/api/v1/payment-methods/me');
}
