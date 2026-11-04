import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { GET } from './route';
import { apiProfileFixture } from '@/tests/helpers/apiProfileFixture';
const call = (slug = 'api-developer') => GET(new Request('http://localhost/api/profiles/' + slug), { params: Promise.resolve({ slug }) });
beforeEach(() => { vi.stubEnv('AUTH_MODE', 'server'); vi.mocked(backendFetch).mockReset(); });
afterEach(() => vi.unstubAllEnvs());
describe('프로필 BFF', () => {
    it('기존 서버 통신을 재사용하고 공개 응답만 no-store로 반환한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...apiProfileFixture, email: 'private', passwordHash: 'private' }));
        const response = await call();
        expect(response.status).toBe(200);
        expect(response.headers.get('cache-control')).toBe('no-store');
        expect(await response.json()).toEqual(apiProfileFixture);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/users/api-developer');
    });
    it.each(['../auth/me', '//example.com', 'bad?tag', ''])('잘못된 태그 %s를 전달하지 않는다', async slug => {
        expect((await call(slug)).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it.each([401, 403, 404])('접근·미존재 상태 %s 보존', async status => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('internal details', { status }));
        const response = await call();
        expect(response.status).toBe(status);
        expect(await response.text()).not.toContain('internal details');
    });
    it('타임아웃과 연결 오류에 내부 주소를 노출하지 않는다', async () => {
        vi.mocked(backendFetch).mockRejectedValue(new Error('http://internal-db:8080 secret'));
        const response = await call();
        expect(response.status).toBe(502);
        expect(await response.text()).not.toContain('secret');
    });
    it.each([{}, { ...apiProfileFixture, tag: 'other' }])('손상되거나 태그가 다른 응답에 502', async body => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(body));
        expect((await call()).status).toBe(502);
    });
    it('백엔드 500은 게이트웨이 오류로 표시한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('stack trace', { status: 500 }));
        expect((await call()).status).toBe(502);
    });
});
