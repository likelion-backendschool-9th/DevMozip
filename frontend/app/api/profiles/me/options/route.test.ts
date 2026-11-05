import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { GET, PUT } from './route';
import { GET as publicGet } from '../../[slug]/options/route';
const url = 'http://localhost/api/profiles/me/options';
const put = (body: unknown, origin = 'http://localhost') => PUT(new Request(url, { method: 'PUT', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: JSON.stringify(body) }));
beforeEach(() => vi.mocked(backendFetch).mockReset());
describe('프로필 선택 정보 BFF', () => {
    it('공개 응답을 제한하고 캐시하지 않는다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ skills: ['Java'], interests: ['AI'], email: 'private' }));
        const result = await publicGet(new Request('http://localhost'), { params: Promise.resolve({ slug: 'member-tag' }) });
        expect(await result.json()).toEqual({ skills: ['Java'], interests: ['AI'] });
        expect(result.headers.get('cache-control')).toBe('no-store');
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/users/member-tag/options', {});
    });
    it('본인 조회 경로를 사용한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ skills: [], interests: [] }));
        expect((await GET()).status).toBe(200);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/users/me/options', {});
    });
    it('수정할 두 목록만 전달하고 명시적 삭제를 허용한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ skills: [], interests: [] }));
        expect((await put({ skills: [], interests: [], id: 2, role: 'ADMIN' })).status).toBe(200);
        expect(JSON.parse(String(vi.mocked(backendFetch).mock.calls[0][1]?.body))).toEqual({ skills: [], interests: [] });
    });
    it.each([{}, { skills: null, interests: [] }, { skills: [], interests: [null] }, { skills: Array(31).fill('Java'), interests: [] }])('잘못된 형식 거절', async input => {
        expect((await put(input)).status).toBe(400); expect(backendFetch).not.toHaveBeenCalled();
    });
    it('외부 출처의 변경을 차단한다', async () => {
        expect((await put({ skills: [], interests: [] }, 'https://other.test')).status).toBe(403);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it.each([400, 401, 403, 404])('서버 오류 %s를 유지하고 내부 내용을 숨긴다', async status => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('secret', { status }));
        const result = await put({ skills: ['Unknown'], interests: [] });
        expect(result.status).toBe(status); expect(await result.text()).not.toContain('secret');
    });
    it('깨진 응답과 잘못된 태그를 차단한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ skills: null, interests: [] }));
        expect((await GET()).status).toBe(502); vi.mocked(backendFetch).mockReset();
        expect((await publicGet(new Request('http://localhost'), { params: Promise.resolve({ slug: '../me' }) })).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
});
