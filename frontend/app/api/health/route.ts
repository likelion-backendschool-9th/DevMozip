import { NextResponse } from 'next/server';
import { BACKEND_URL } from '@/lib/auth/env';
export async function GET() {
    try {
        const response = await fetch(`${BACKEND_URL}/actuator/health`, { cache: 'no-store', signal: AbortSignal.timeout(3000) });
        const body = await response.json();
        if (response.ok && body.status === 'UP') return NextResponse.json({ status: 'UP' });
    } catch { /* 도메인 데이터나 쿠키 없이 백엔드 상태만 확인한다. */ }
    return NextResponse.json({ status: 'DOWN' }, { status: 503 });
}
