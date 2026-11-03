import { NextResponse } from 'next/server';
import { authMode } from '@/lib/auth/env';
import { getSession } from '@/lib/auth/session';
export async function GET() {
    const mode = authMode();
    try {
        const session = mode === 'server' ? await getSession() : null;
        return NextResponse.json({ mode, identity: session ? { username: session.username, role: session.role } : null }, { headers: { 'Cache-Control': 'no-store' } });
    } catch {
        return NextResponse.json({ mode, identity: null, status: 502, error: 'Bad Gateway', message: '인증 서버에 연결할 수 없습니다.', path: '/api/auth/session', timestamp: new Date().toISOString() }, { status: 502, headers: { 'Cache-Control': 'no-store' } });
    }
}
