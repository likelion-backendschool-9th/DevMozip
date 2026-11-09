import { afterEach, describe, expect, it, vi } from 'vitest';
import { POST } from './route';
import { PUT, DELETE } from './[id]/route';
import { backendFetch } from '@/lib/auth/backend';
import { portfolioDetailFixture } from '@/tests/helpers/portfolioApiFixture';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
afterEach(() => vi.resetAllMocks());
const request = (method = 'POST', body: unknown = { title: '제목', userId: 999, role: '백엔드' }, origin = 'http://localhost') => new Request('http://localhost/api/portfolios', {
    method, headers: { Origin: origin, 'Content-Type': 'application/json' }, ...(method === 'DELETE' ? {} : { body: JSON.stringify(body) }),
});
const params = (id = '11') => ({ params: Promise.resolve({ id }) });
describe('포트폴리오 쓰기 BFF', () => {
    it('등록 201·Location을 반환하고 사용자 식별자 주입을 제거한다', async () => {
        const d = portfolioDetailFixture(); d.canEdit = true; vi.mocked(backendFetch).mockResolvedValue(Response.json(d, { status: 201 }));
        const response = await POST(request()); expect(response.status).toBe(201); expect(response.headers.get('Location')).toBe('/api/portfolios/11');
        expect(response.headers.get('Cache-Control')).toBe('no-store');
        const [, init] = vi.mocked(backendFetch).mock.calls[0]; expect(init?.method).toBe('POST'); expect(JSON.parse(init?.body as string)).not.toHaveProperty('userId');
    });
    it('외부 출처와 누락된 Origin을 거절한다', async () => {
        expect((await POST(request('POST', {}, 'http://other.test'))).status).toBe(403);
        expect((await POST(new Request('http://localhost/api/portfolios', { method: 'POST', body: '{}' }))).status).toBe(403); expect(backendFetch).not.toHaveBeenCalled();
    });
    it('JSON 아닌 요청·배열·깨진 JSON을 거절한다', async () => {
        expect((await POST(new Request('http://localhost/api/portfolios', { method: 'POST', headers: { Origin: 'http://localhost' }, body: '{}' }))).status).toBe(415);
        expect((await POST(request('POST', []))).status).toBe(400);
        expect((await POST(new Request('http://localhost/api/portfolios', { method: 'POST', headers: { Origin: 'http://localhost', 'Content-Type': 'application/json' }, body: '{' }))).status).toBe(400);
    });
    it('Content-Length를 믿지 않고 실제 크기를 제한한다', async () => {
        const r = request('POST', { description: 'x'.repeat(2 * 1024 * 1024) }); r.headers.set('Content-Length', '1');
        expect((await POST(r)).status).toBe(413); expect(backendFetch).not.toHaveBeenCalled();
    });
    it('PUT ID·소유권 응답이 일치해야 한다', async () => {
        const d = portfolioDetailFixture(); d.canEdit = true;
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json(d)); expect((await PUT(request('PUT'), params())).status).toBe(200);
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json(d)); expect((await PUT(request('PUT'), params('12'))).status).toBe(502);
        d.canEdit = false; vi.mocked(backendFetch).mockResolvedValueOnce(Response.json(d)); expect((await PUT(request('PUT'), params())).status).toBe(502);
    });
    it.each([401, 403, 404, 409, 503])('쓰기 실패 %i를 보존하고 내부 오류는 숨긴다', async status => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ detail: 'SELECT secret' }, { status }));
        const response = await POST(request()); expect(response.status).toBe(status); expect(await response.text()).not.toContain('SELECT');
    });
    it('DELETE는 같은 출처와 204를 요구한다', async () => {
        expect((await DELETE(request('DELETE', {}, 'http://evil.test'), params())).status).toBe(403);
        vi.mocked(backendFetch).mockResolvedValue(new Response(null, { status: 204 }));
        const response = await DELETE(request('DELETE'), params()); expect(response.status).toBe(204); expect(await response.text()).toBe('');
    });
    it('유효하지 않은 ID는 요청하지 않는다', async () => {
        expect((await PUT(request('PUT'), params('-1'))).status).toBe(400); expect((await DELETE(request('DELETE'), params('abc'))).status).toBe(400); expect(backendFetch).not.toHaveBeenCalled();
    });
});
