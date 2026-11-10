import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
vi.mock('@/lib/auth/session', () => ({ getSession: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { getSession } from '@/lib/auth/session';
import { GET } from './route';
import { PATCH } from './[slug]/ban/route';

const member = { id: 3, tag: 'kim', nickname: '김개발', email: 'kim@test.local', role: 'USER', status: 'WARNED',
    latestSanction: { type: 'WARNED', reason: '운영 정책 위반', adminNickname: '운영자', startsAt: '2026-11-10T10:00:00', endsAt: null, revokedAt: null } };
const page = { items: [member], totalElements: 1, page: 0, totalPages: 1 };
const list = (search = '') => GET(new Request(`http://localhost/api/admin/users${search}`));
const context = (slug = 'kim') => ({ params: Promise.resolve({ slug }) });
const ban = (body: string, origin: string | null = 'http://localhost', slug = 'kim') =>
    PATCH(new Request(`http://localhost/api/admin/users/${slug}/ban`, {
        method: 'PATCH', body, headers: { 'Content-Type': 'application/json', ...(origin ? { Origin: origin } : {}) },
    }), context(slug));
beforeEach(() => {
    vi.mocked(backendFetch).mockReset();
    vi.mocked(getSession).mockReset();
    vi.mocked(getSession).mockResolvedValue({ username: 'admin@devmozip.demo', role: 'ADMIN', isAdmin: true, token: 't' } as never);
});

describe('회원 조회 BFF (14.2)', () => {
    it('확인한 조건만 보내고 허용한 필드만 돌려준다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...page, items: [{ ...member, passwordHash: 'x', kakaoId: '1' }] }));
        const response = await list('?q=%20kim%20&status=WARNED&role=USER&page=0&size=20&userId=1');
        expect(response.status).toBe(200);
        expect(await response.json()).toEqual(page);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/admin/users?page=0&size=20&q=kim&status=WARNED&role=USER', {});
    });
    it.each(['?status=DELETED', '?role=root', '?page=-1', '?q=a&q=b'])('잘못된 조건 %s는 400이다', async search => {
        expect((await list(search)).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('관리자가 아니면 백엔드를 부르지 않는다', async () => {
        vi.mocked(getSession).mockResolvedValue({ username: 'm@test', role: 'USER', isAdmin: false, token: 't' } as never);
        expect((await list()).status).toBe(403);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('형식이 다른 응답은 502다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...page, items: [{ ...member, status: 'DELETED' }] }));
        expect((await list()).status).toBe(502);
    });
});

describe('회원 제재 BFF (14.3)', () => {
    it('확인한 본문만 PATCH로 보낸다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(member));
        const response = await ban('{"status":"WARNED","reason":"  운영 정책 위반 ","reportId":5}');
        expect(response.status).toBe(200);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/admin/users/kim/ban',
            { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: '{"status":"WARNED","reason":"운영 정책 위반","reportId":5}' });
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...member, status: 'ACTIVE' }));
        await ban('{"status":"ACTIVE"}');
        expect(vi.mocked(backendFetch).mock.calls[1][1]).toEqual({ method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: '{"status":"ACTIVE"}' });
    });
    it.each([
        ['{"status":"WARNED"}', '경고·정지 사유를 입력해 주세요.'],
        ['{"status":"SUSPENDED","reason":"  "}', '경고·정지 사유를 입력해 주세요.'],
        ['{"status":"DELETED","reason":"x"}', '변경할 상태를 확인해 주세요.'],
        ['{"status":"WARNED","reason":12}', '사유를 확인해 주세요.'],
        ['{"status":"WARNED","reason":"x","userId":1}', '요청 내용을 확인해 주세요.'],
        ['{"status":"WARNED","reason":"x","reportId":"5"}', '근거 신고를 확인해 주세요.'],
        [`{"status":"WARNED","reason":"${'가'.repeat(301)}"}`, '사유는 300자 이하로 입력해 주세요.'],
        ['{', '요청 내용을 확인해 주세요.'],
        ['{"status":"ACTIVE","reason":"풀어 줌"}', '해제에는 사유와 근거 신고를 함께 보내지 않아요.'],
        ['{"status":"ACTIVE","reportId":5}', '해제에는 사유와 근거 신고를 함께 보내지 않아요.'],
    ])('잘못된 본문 %s는 400이다', async (body, message) => {
        const response = await ban(body);
        expect(response.status).toBe(400);
        expect((await response.json()).message).toBe(message);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('다른 사이트 요청과 잘못된 태그는 백엔드로 보내지 않는다', async () => {
        expect((await ban('{"status":"ACTIVE"}', 'http://evil.example')).status).toBe(403);
        expect((await ban('{"status":"ACTIVE"}', 'http://localhost', '../users')).status).toBe(404);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('백엔드의 409 안내를 그대로 보여 준다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ message: '이미 같은 상태인 회원입니다.' }, { status: 409 }));
        const response = await ban('{"status":"ACTIVE"}');
        expect(response.status).toBe(409);
        expect((await response.json()).message).toBe('이미 같은 상태인 회원입니다.');
    });
});
