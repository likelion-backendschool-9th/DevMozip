import { NextResponse } from 'next/server';
import { getSession } from '@/lib/auth/session';
import type { ServerAccountStatus } from '@/types/session';

const statuses: readonly ServerAccountStatus[] = ['ACTIVE', 'WARNED', 'SUSPENDED'];

export async function GET() {
    try {
        const session = await getSession();
        // 계정 상태와 경고 사유는 형식을 확인한 값만 넘긴다(docs/admin-plan.md D8). 그 밖의 값은 null이다.
        const status = session && statuses.includes(session.status as ServerAccountStatus) ? session.status as ServerAccountStatus : null;
        const warningReason = status === 'WARNED' && typeof session?.warningReason === 'string' ? session.warningReason : null;
        return NextResponse.json({ identity: session ? { username: session.username, role: session.role, status, warningReason } : null }, { headers: { 'Cache-Control': 'no-store' } });
    } catch {
        return NextResponse.json({ identity: null, status: 502, error: 'Bad Gateway', message: '인증 서버에 연결할 수 없습니다.', path: '/api/auth/session', timestamp: new Date().toISOString() }, { status: 502, headers: { 'Cache-Control': 'no-store' } });
    }
}
