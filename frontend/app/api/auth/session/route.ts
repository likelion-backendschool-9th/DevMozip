import { NextResponse } from 'next/server';
import { getSession } from '@/lib/auth/session';
export async function GET() {
    try {
        const session = await getSession();
        return NextResponse.json({ identity: session ? { username: session.username, role: session.role } : null }, { headers: { 'Cache-Control': 'no-store' } });
    } catch {
        return NextResponse.json({ identity: null, status: 502, error: 'Bad Gateway', message: '인증 서버에 연결할 수 없습니다.', path: '/api/auth/session', timestamp: new Date().toISOString() }, { status: 502, headers: { 'Cache-Control': 'no-store' } });
    }
}
