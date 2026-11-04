import type { ServerRole } from '@/types/session';
export interface TokenPayload { sub: string; role?: ServerRole; exp: number }
const roles: unknown[] = ['USER', 'PREMIUM', 'ADMIN'] satisfies ServerRole[];
// 화면 이동 판단용 디코딩이다. 실제 인가는 백엔드 서명 검증을 거친다.
export function decodeToken(token?: string): TokenPayload | null {
    try {
        if (!token || token.split('.').length !== 3) return null;
        const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));
        if (typeof payload.sub !== 'string' || !payload.sub || typeof payload.exp !== 'number' || !Number.isFinite(payload.exp)) return null;
        if (payload.role !== undefined && !roles.includes(payload.role)) return null;
        return payload;
    } catch { return null; }
}
export function isExpired(payload: TokenPayload | null) {
    return !payload || payload.exp * 1000 <= Date.now();
}
