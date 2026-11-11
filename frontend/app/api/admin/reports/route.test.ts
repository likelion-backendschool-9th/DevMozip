import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
vi.mock('@/lib/auth/session', () => ({ getSession: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { getSession } from '@/lib/auth/session';
import { GET } from './route';
import { PATCH } from './[id]/route';

const report = {
    id: 5, type: 'POST', reporter: { id: 2, tag: 'reporter', nickname: '신고자', withdrawn: false },
    target: { projectId: null, projectTitle: null, user: { id: 3, tag: 'target', nickname: '대상자', withdrawn: false }, postId: 40, commentId: null, content: '신고된 글', deleted: false },
    reason: '스팸', status: 'AWATING', resolution: null, handledByNickname: null, handledAt: null, createdAt: '2026-11-10T10:00:00',
};
const list = (search = '') => GET(new Request(`http://localhost/api/admin/reports${search}`));
const patch = (body: string, origin = 'http://localhost', id = '5') =>
    PATCH(new Request(`http://localhost/api/admin/reports/${id}`, { method: 'PATCH', body, headers: { 'Content-Type': 'application/json', Origin: origin } }),
        { params: Promise.resolve({ id }) });
beforeEach(() => {
    vi.mocked(backendFetch).mockReset();
    vi.mocked(getSession).mockReset();
    vi.mocked(getSession).mockResolvedValue({ username: 'admin@devmozip.demo', role: 'ADMIN', isAdmin: true, token: 't' } as never);
});

describe('신고 내역 BFF (14.8, D5)', () => {
    it('종류·상태 조건만 보내고 응답 형식을 확인한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ items: [{ ...report, reporterEmail: 'x' }], totalElements: 1, page: 0, totalPages: 1 }));
        const body = await (await list('?type=POST&status=AWATING&page=1&size=20&reporterId=9')).json();
        expect(body.items).toEqual([report]);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/admin/reports?page=1&size=20&type=POST&status=AWATING', {});
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ items: [{ ...report, target: { ...report.target, deleted: 'no' } }], totalElements: 1, page: 0, totalPages: 1 }));
        expect((await list()).status).toBe(502);
    });
    it.each(['?type=FEED', '?status=RECEIVED', '?page=-1', '?size=bad'])('잘못된 조건 %s는 400이다', async search => {
        expect((await list(search)).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('관리자가 아니면 백엔드를 부르지 않는다', async () => {
        vi.mocked(getSession).mockResolvedValue({ username: 'm@test', role: 'PREMIUM', isAdmin: false, token: 't' } as never);
        expect((await list()).status).toBe(403);
        expect(backendFetch).not.toHaveBeenCalled();
    });
});

describe('신고 처리 BFF (14.8)', () => {
    it('처리자를 본문에서 받지 않고 상태·결과만 보낸다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...report, status: 'RESOLVED', resolution: '숨김 처리' }));
        const response = await patch('{"status":"RESOLVED","resolution":" 숨김 처리 "}');
        expect(response.status).toBe(200);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/admin/reports/5',
            { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: '{"status":"RESOLVED","resolution":"숨김 처리"}' });
    });
    it.each([
        ['{"status":"AWATING","resolution":"메모"}', '처리 상태를 확인해 주세요.'],
        ['{"status":"RESOLVED","resolution":""}', '처리 결과를 입력해 주세요.'],
        ['{"status":"REJECTED"}', '처리 결과를 입력해 주세요.'],
        [`{"status":"REJECTED","resolution":"${'x'.repeat(1001)}"}`, '처리 결과는 1,000자 이하로 입력해 주세요.'],
        ['{"status":"RESOLVED","resolution":"안내 완료","handledBy":999}', '요청 내용을 확인해 주세요.'],
        ['{"status":"RESOLVED","resolution":7}', '처리 결과를 확인해 주세요.'],
    ])('잘못된 결정 %s는 400이다', async (body, message) => {
        const response = await patch(body);
        expect(response.status).toBe(400);
        expect((await response.json()).message).toBe(message);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('검토 중은 결과 없이 보낼 수 있다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...report, status: 'REVIEWING' }));
        await patch('{"status":"REVIEWING"}');
        expect(vi.mocked(backendFetch).mock.calls[0][1]).toEqual({ method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: '{"status":"REVIEWING"}' });
    });
    it('다른 사이트 요청과 잘못된 ID는 보내지 않고, 이미 처리한 신고의 409 안내를 전달한다', async () => {
        expect((await patch('{"status":"REVIEWING"}', 'http://evil.example')).status).toBe(403);
        expect((await patch('{"status":"REVIEWING"}', 'http://localhost', 'abc')).status).toBe(404);
        expect(backendFetch).not.toHaveBeenCalled();
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ message: '이미 처리가 끝난 신고입니다.' }, { status: 409 }));
        const response = await patch('{"status":"REVIEWING"}');
        expect(response.status).toBe(409);
        expect((await response.json()).message).toBe('이미 처리가 끝난 신고입니다.');
    });
});
