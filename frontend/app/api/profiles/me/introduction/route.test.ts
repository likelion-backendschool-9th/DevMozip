import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { GET, PUT } from './route';
import { GET as publicGet } from '../../[slug]/introduction/route';
const url = 'http://localhost/api/profiles/me/introduction';
const put = (body: unknown, origin = 'http://localhost') => PUT(new Request(url, { method: 'PUT', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: JSON.stringify(body) }));
beforeEach(() => vi.mocked(backendFetch).mockReset());
describe('상세 자기소개 BFF', () => {
    it('공개 조회는 태그 경로로 전달하고 공개 필드만 반환한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ introduction: '소개', format: 'text', email: 'private' }));
        const result = await publicGet(new Request('http://localhost'), { params: Promise.resolve({ slug: 'member-tag' }) });
        expect(await result.json()).toEqual({ introduction: '소개', format: 'text' });
        expect(result.headers.get('cache-control')).toBe('no-store');
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/users/member-tag/introduction', {});
    });
    it('내 소개 조회는 전용 경로로 전달한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ introduction: null, format: 'text' }));
        expect((await GET()).status).toBe(200);
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/users/me/introduction', {});
    });
    it('수정 요청은 자기소개와 형식만 전달하고 명시적 삭제를 유지한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ introduction: null, format: 'text' }));
        expect((await put({ introduction: '', format: 'text', id: 2, email: 'other', role: 'ADMIN' })).status).toBe(200);
        const [path, init] = vi.mocked(backendFetch).mock.calls[0];
        expect(path).toBe('/api/v1/users/me/introduction');
        expect(JSON.parse(String(init?.body))).toEqual({ introduction: '', format: 'text' });
    });
    it.each([{}, { introduction: null, format: 'text' }, { introduction: '값', format: 'markdown' }, { introduction: '가'.repeat(20001), format: 'text' }])('잘못된 입력을 전달하지 않는다', async input => {
        expect((await put(input)).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('외부 출처의 수정 요청을 차단한다', async () => {
        expect((await put({ introduction: '값', format: 'text' }, 'https://other.test')).status).toBe(403);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it.each([401, 403, 404])('백엔드 상태 %s를 유지하고 내부 오류를 숨긴다', async status => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('secret stack trace', { status }));
        const result = await put({ introduction: '값', format: 'text' });
        expect(result.status).toBe(status);
        expect(await result.text()).not.toContain('secret');
    });
    it('깨진 응답은 소개 없음으로 위장하지 않고 502로 표시한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ introduction: null, format: 'unknown' }));
        expect((await GET()).status).toBe(502);
    });
    it('잘못된 태그 경로를 전달하지 않는다', async () => {
        expect((await publicGet(new Request('http://localhost'), { params: Promise.resolve({ slug: '../me' }) })).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
});
