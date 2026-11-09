import { forward, jsonError } from '@/lib/subscription/server';

// 13.5 결제 내역
export async function GET(request: Request) {
    const params = new URL(request.url).searchParams;
    const page = Number(params.get('page') ?? '0');
    const size = Number(params.get('size') ?? '20');
    if (!Number.isSafeInteger(page) || page < 0 || !Number.isSafeInteger(size) || size < 1 || size > 100) return jsonError(400, '페이지 범위를 확인해 주세요.');
    return forward(`/api/v1/payments/me?page=${page}&size=${size}`);
}
