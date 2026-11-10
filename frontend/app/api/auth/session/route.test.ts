import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/session', () => ({ getSession: vi.fn() }));
import { getSession } from '@/lib/auth/session';
import { GET } from './route';

const session = (extra: Record<string, unknown>) => ({ username: 'kim@test.local', role: 'USER', isAdmin: false, token: 'secret-token', ...extra });
beforeEach(() => { vi.mocked(getSession).mockReset(); });

describe('세션 BFF의 계정 상태 (docs/admin-plan.md D8)', () => {
    it('경고 회원의 상태와 사유를 넘기고 토큰은 넘기지 않는다', async () => {
        vi.mocked(getSession).mockResolvedValue(session({ status: 'WARNED', warningReason: '운영 정책 위반' }) as never);
        const body = await (await GET()).json();
        expect(body).toEqual({ identity: { username: 'kim@test.local', role: 'USER', status: 'WARNED', warningReason: '운영 정책 위반' } });
        expect(JSON.stringify(body)).not.toContain('secret-token');
    });
    it('경고가 아니면 사유를 넘기지 않고, 모르는 상태는 null이다', async () => {
        vi.mocked(getSession).mockResolvedValue(session({ status: 'ACTIVE', warningReason: '남은 값' }) as never);
        expect((await (await GET()).json()).identity).toEqual({ username: 'kim@test.local', role: 'USER', status: 'ACTIVE', warningReason: null });
        vi.mocked(getSession).mockResolvedValue(session({ status: 'DELETED' }) as never);
        expect((await (await GET()).json()).identity.status).toBeNull();
        vi.mocked(getSession).mockResolvedValue(session({}) as never);
        expect((await (await GET()).json()).identity).toEqual({ username: 'kim@test.local', role: 'USER', status: null, warningReason: null });
    });
    it('로그인하지 않았으면 identity가 null이다', async () => {
        vi.mocked(getSession).mockResolvedValue(null);
        expect(await (await GET()).json()).toEqual({ identity: null });
    });
});
