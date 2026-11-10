import { describe, expect, it } from 'vitest';
import { decodeToken, isExpired } from './token';
import { isProtectedPath, safeNext } from './paths';
import { projectServerIdentity } from '@/lib/account/serverIdentity';
const token = (data: unknown) => `header.${Buffer.from(JSON.stringify(data)).toString('base64url')}.signature`;
describe('인증 경계', () => {
    it('만료와 손상된 토큰을 식별한다', () => {
        expect(isExpired(decodeToken(token({ sub: 'user', role: 'USER', exp: 1 })))).toBe(true);
        expect(decodeToken('broken')).toBeNull();
        expect(decodeToken(token({ sub: 'user', role: 'SUPER', exp: 9999999999 }))).toBeNull();
        expect(isExpired(decodeToken(token({ sub: 'user', role: 'USER', exp: 9999999999 })))).toBe(false);
        expect(decodeToken(token({ sub: 'user', role: 'PREMIUM', exp: 9999999999 }))?.role).toBe('PREMIUM');
    });
    it.each(['PREMIUM', 'OWNER'])('실제 회원 역할 %s의 로그인 토큰을 유지한다', role => {
        expect(decodeToken(token({ sub: 'member@profile.test', role, exp: 9999999999 }))?.role).toBe(role);
    });
    it('외부 리다이렉트 및 역슬래시 우회를 거절한다', () => {
        for (const path of ['//evil.test', '/\\evil.test', 'https://evil.test', '/ /evil', null]) expect(safeNext(path)).toBe('/');
        expect(safeNext('/messages?userId=demo-user')).toBe('/messages?userId=demo-user');
    });
    it('개인 정보와 작성·수정·관리 경로를 보호하고 공개 조회는 유지한다', () => {
        for (const path of ['/admin/users', '/settings/account', '/messages', '/projects/a/manage', '/projects/123/edit', '/projects/a/reviews/write', '/posts/new', '/posts/a/edit', '/portfolios/a', '/chats/a', '/bookmarks', '/feeds/new', '/feeds/a/edit', '/projects/a/reviews']) expect(isProtectedPath(path)).toBe(true);
        for (const path of ['/', '/feed', '/projects/a', '/posts/a', '/users/a', '/administrator']) expect(isProtectedPath(path)).toBe(false);
    });
    it('데모 프로필이 서버 역할을 올릴 수 없다', () => {
        expect(projectServerIdentity({ username: 'admin@devmozip.demo', role: 'USER' }).role).toBe('free');
        expect(projectServerIdentity({ username: 'premium@devmozip.demo', role: 'USER' }).role).toBe('free');
        expect(projectServerIdentity({ username: 'admin@devmozip.demo', role: 'ADMIN' }).role).toBe('admin');
        expect(projectServerIdentity({ username: 'admin@devmozip.demo', role: 'PREMIUM' })).toMatchObject({ id: 'demo-user', role: 'premium' });
    });
    it('서버 PREMIUM 역할을 프리미엄 회원으로 표시한다', () => {
        expect(projectServerIdentity({ username: 'premium@devmozip.demo', role: 'PREMIUM' })).toMatchObject({ id: 'demo-premium', role: 'premium' });
    });
});
