import { afterEach, describe, expect, it, vi } from 'vitest';
import { PATCH } from './route';
import { backendFetch } from '@/lib/auth/backend';
import { portfolioDetailFixture } from '@/tests/helpers/portfolioApiFixture';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
afterEach(() => vi.resetAllMocks());
const params = (id = '11') => ({ params: Promise.resolve({ id }) });
function request(body: unknown = { visibility: 'PRIVATE', expectedUpdatedAt: null }, origin = 'http://localhost:3000') {
    return new Request('http://0.0.0.0:3000/api/portfolios/11/visibility', { method: 'PATCH',
        headers: { Host: 'localhost:3000', Origin: origin, 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
}
describe('포트폴리오 공개 범위 BFF', () => {
    it('소유권·ID·결과 범위를 확인하고 허용 필드만 전달한다', async () => {
        const value = portfolioDetailFixture(); value.canEdit = true; value.portfolio.visibility = 'PRIVATE';
        vi.mocked(backendFetch).mockResolvedValue(Response.json(value));
        const response = await PATCH(request({ visibility: 'PRIVATE', expectedUpdatedAt: null, userId: 999, title: '변조' }), params());
        expect(response.status).toBe(200); expect(response.headers.get('Cache-Control')).toBe('no-store');
        const [path, init] = vi.mocked(backendFetch).mock.calls[0];
        expect(path).toBe('/api/v1/portfolios/11/visibility'); expect(init?.method).toBe('PATCH');
        expect(JSON.parse(init?.body as string)).toEqual({ visibility: 'PRIVATE', expectedUpdatedAt: null });
    });
    it.each([{ visibility: 'public', expectedUpdatedAt: null }, { visibility: 'PUBLIC' }, { visibility: null, expectedUpdatedAt: null }, []])('잘못된 상태·누락 버전을 거절한다', async body => {
        expect((await PATCH(request(body), params())).status).toBe(400); expect(backendFetch).not.toHaveBeenCalled();
    });
    it('다른 출처·임의 ID는 거절한다', async () => {
        expect((await PATCH(request(undefined, 'http://evil.test'), params())).status).toBe(403);
        expect((await PATCH(request(), params('bad'))).status).toBe(400); expect(backendFetch).not.toHaveBeenCalled();
    });
    it('다른 기록·권한·공개 값의 응답을 성공으로 표시하지 않는다', async () => {
        for (const edit of [(v: ReturnType<typeof portfolioDetailFixture>) => { v.portfolio.id = 12; },
            (v: ReturnType<typeof portfolioDetailFixture>) => { v.canEdit = false; },
            (v: ReturnType<typeof portfolioDetailFixture>) => { v.portfolio.visibility = 'PUBLIC'; }]) {
            const value = portfolioDetailFixture(); value.canEdit = true; value.portfolio.visibility = 'PRIVATE'; edit(value);
            vi.mocked(backendFetch).mockResolvedValue(Response.json(value)); expect((await PATCH(request(), params())).status).toBe(502);
        }
    });
    it('동시 수정·제출 제한 409는 유지하고 내부 문구를 숨긴다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ detail: 'internal SQL' }, { status: 409 }));
        const response = await PATCH(request(), params()); expect(response.status).toBe(409); expect(await response.text()).not.toContain('internal SQL');
    });
});
