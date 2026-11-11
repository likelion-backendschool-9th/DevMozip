import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
vi.mock('@/lib/auth/session', () => ({ getSession: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { getSession } from '@/lib/auth/session';
import { adminGuard, proxyAdmin, readAdminBody } from './adminProxy';

const session = (role: string) => ({ username: 'admin@devmozip.demo', role, isAdmin: role === 'ADMIN', token: 't' });
const write = (origin: string | null) =>
    new Request('http://localhost/api/admin/x', { method: 'PATCH', headers: origin ? { Origin: origin } : {} });
beforeEach(() => { vi.mocked(backendFetch).mockReset(); vi.mocked(getSession).mockReset(); });

describe('관리자 BFF 가드', () => {
    it('관리자 세션이면 통과한다', async () => {
        vi.mocked(getSession).mockResolvedValue(session('ADMIN') as never);
        expect(await adminGuard(new Request('http://localhost/api/admin/x'))).toBeNull();
        expect(await adminGuard(write('http://localhost'), true)).toBeNull();
    });
    it('세션이 없으면 401, 관리자가 아니면 403이다', async () => {
        vi.mocked(getSession).mockResolvedValue(null);
        expect((await adminGuard(new Request('http://localhost/api/admin/x')))?.status).toBe(401);
        for (const role of ['USER', 'PREMIUM', 'OWNER']) {
            vi.mocked(getSession).mockResolvedValue(session(role) as never);
            expect((await adminGuard(new Request('http://localhost/api/admin/x')))?.status).toBe(403);
        }
    });
    it('쓰기 요청은 같은 사이트에서만 받고, 세션 확인 전에 막는다', async () => {
        vi.mocked(getSession).mockResolvedValue(session('ADMIN') as never);
        for (const origin of [null, 'http://evil.example', 'https://localhost']) {
            const blocked = await adminGuard(write(origin), true);
            expect(blocked?.status).toBe(403);
            expect((await blocked!.json()).message).toBe('같은 사이트에서 요청해 주세요.');
        }
        expect(getSession).not.toHaveBeenCalled();
    });
    it('세션 확인이 실패하면 502다', async () => {
        vi.mocked(getSession).mockRejectedValue(new Error('down'));
        expect((await adminGuard(new Request('http://localhost/api/admin/x')))?.status).toBe(502);
    });
});

describe('관리자 BFF 전달', () => {
    const parse = (value: unknown) => ({ ok: (value as { ok: boolean }).ok });
    it('형식을 확인한 값만 no-store로 넘긴다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ok: true, secret: 'x' }));
        const response = await proxyAdmin('/api/v1/admin/x', {}, parse);
        expect(response.status).toBe(200);
        expect(response.headers.get('cache-control')).toBe('no-store');
        expect(await response.json()).toEqual({ ok: true });
    });
    it('파싱이 실패하면 502다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({}));
        expect((await proxyAdmin('/api/v1/admin/x', {}, () => { throw new Error('bad'); })).status).toBe(502);
    });
    it('204와 본문 없는 응답을 넘긴다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response(null, { status: 204 }));
        expect((await proxyAdmin('/api/v1/admin/x', { method: 'PUT' }, null)).status).toBe(204);
    });
    it('400·404·409는 백엔드 안내를, 401·403은 고정 문구를 쓰고 5xx는 숨긴다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ message: '경고·정지 사유를 입력해 주세요.' }, { status: 400 }));
        expect(await (await proxyAdmin('/x', {}, parse)).json()).toEqual({ message: '경고·정지 사유를 입력해 주세요.' });
        vi.mocked(backendFetch).mockResolvedValue(new Response('', { status: 409 }));
        const conflict = await proxyAdmin('/x', {}, parse);
        expect(conflict.status).toBe(409);
        expect((await conflict.json()).message).toContain('현재 상태');
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ message: 'Access Denied' }, { status: 403 }));
        expect((await (await proxyAdmin('/x', {}, parse)).json()).message).toBe('관리자만 이용할 수 있어요.');
        vi.mocked(backendFetch).mockResolvedValue(new Response('stack trace secret', { status: 500 }));
        const failed = await proxyAdmin('/x', {}, parse);
        expect(failed.status).toBe(502);
        expect(await failed.text()).not.toContain('secret');
    });
    it('연결 오류는 502이며 내부 내용을 숨긴다', async () => {
        vi.mocked(backendFetch).mockRejectedValue(new Error('http://backend:8080 secret'));
        const response = await proxyAdmin('/x', {}, parse);
        expect(response.status).toBe(502);
        expect(await response.text()).not.toContain('secret');
    });
});

describe('관리자 BFF 본문', () => {
    const body = (text: string, type = 'application/json') =>
        new Request('http://localhost/x', { method: 'PATCH', headers: { 'Content-Type': type }, body: text });
    it('JSON 객체만 읽는다', async () => {
        expect(await readAdminBody(body('{"a":1}'))).toEqual({ a: 1 });
        for (const text of ['{', '[1]', 'null', '"x"']) expect(await readAdminBody(body(text))).toBeNull();
        expect(await readAdminBody(body('{"a":1}', 'text/plain'))).toBeNull();
    });
});
