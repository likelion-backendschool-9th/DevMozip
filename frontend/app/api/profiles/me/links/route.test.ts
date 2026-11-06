import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { GET, PUT } from './route';
import { GET as publicGet } from '../../[slug]/links/route';
const empty = { github: '', blog: '', externalLinks: [] };
const put = (body: unknown, origin = 'http://localhost') => PUT(new Request('http://localhost/api/profiles/me/links', { method: 'PUT', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: JSON.stringify(body) }));
beforeEach(() => vi.mocked(backendFetch).mockReset());
describe('외부 링크 BFF', () => {
    it('공개 조회는 필요한 필드만 전달하고 캐시하지 않는다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...empty, email: 'private' }));
        const response = await publicGet(new Request('http://localhost'), { params: Promise.resolve({ slug: 'member-tag' }) });
        expect(await response.json()).toEqual(empty); expect(response.headers.get('cache-control')).toBe('no-store');
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/users/member-tag/links', {});
    });
    it('본인 조회 및 수정 경로를 사용하며 사용자 지정 필드를 제외한다', async () => {
        vi.mocked(backendFetch).mockImplementation(async () => Response.json(empty));
        expect((await GET()).status).toBe(200);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/users/me/links', {});
        expect((await put({ ...empty, id: 2, role: 'ADMIN' })).status).toBe(200);
        expect(JSON.parse(String(vi.mocked(backendFetch).mock.calls[1][1]?.body))).toEqual(empty);
    });
    it.each([{}, { ...empty, github: null }, { ...empty, blog: 'javascript:alert(1)' }, { ...empty, externalLinks: null }])('잘못된 입력 차단', async body => {
        expect((await put(body)).status).toBe(400); expect(backendFetch).not.toHaveBeenCalled();
    });
    it('외부 출처 및 잘못된 태그 경로 차단', async () => {
        expect((await put(empty, 'https://other.test')).status).toBe(403);
        expect((await publicGet(new Request('http://localhost'), { params: Promise.resolve({ slug: '../me' }) })).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it.each([400, 401, 403, 404, 500])('오류 %s의 내부 정보를 숨긴다', async status => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('private trace', { status }));
        const response = await put(empty); expect(response.status).toBe(status === 500 ? 502 : status);
        expect(await response.text()).not.toContain('private');
    });
    it('위험한 서버 링크를 브라우저에 전달하지 않는다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ ...empty, github: 'javascript:alert(1)' }));
        expect((await GET()).status).toBe(502);
    });
});
