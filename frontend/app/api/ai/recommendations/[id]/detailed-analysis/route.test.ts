import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { GET, POST } from './route';
const context = (id = '7') => ({ params: Promise.resolve({ id }) });
const empty = { recommendationId: 7, status: 'PENDING', cached: false, generatedAt: null, portfolios: [], assessments: [], questions: [], notice: '진행 중' };
const post = (origin = 'http://localhost', body = '{}') => POST(new Request('http://localhost/api/ai/recommendations/7/detailed-analysis', {
    method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json', Authorization: 'Bearer attacker' }, body }), context());
beforeEach(() => vi.mocked(backendFetch).mockReset());
describe('6B 전용 BFF', () => {
    it('쿠키 인증만 사용하고 진행 중 결과·재조회 헤더를 보존한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(empty, { status: 202 }));
        const response = await post();
        expect(response.status).toBe(202); expect(response.headers.get('retry-after')).toBe('3');
        expect(response.headers.get('cache-control')).toBe('no-store');
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/ai/recommendations/7/detailed-analysis', expect.objectContaining({ headers: { 'Content-Type': 'application/json' }, body: '{}' }));
    });
    it('외부 출처와 사용자 ID 주입·잘못된 경로를 차단한다', async () => {
        expect((await post('https://evil.test')).status).toBe(403);
        expect((await post('http://localhost', '{"userId":1}')).status).toBe(400);
        expect((await GET(new Request('http://localhost/api/ai/recommendations/0/detailed-analysis'), context('0'))).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it.each([401, 403, 404, 409, 429, 502, 503, 504])('오류 %s는 민감 응답을 전달하지 않고 원래 상태를 유지한다', async status => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ message: 'private original text', quote: 'secret' }, { status }));
        const response = await post(); expect(response.status).toBe(status); expect(await response.text()).not.toContain('private');
    });
    it('잘못된 성공 응답과 BFF 타임아웃은 오류다', async () => {
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json({ ...empty, recommendationId: 8 }))
            .mockRejectedValueOnce(new DOMException('timeout', 'TimeoutError'));
        expect((await post()).status).toBe(502); expect((await post()).status).toBe(504);
    });
});
