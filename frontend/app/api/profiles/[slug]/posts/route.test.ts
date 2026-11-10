import { beforeEach, expect, it, vi } from 'vitest';
const fetchBackend = vi.hoisted(() => vi.fn());
vi.mock('@/lib/auth/backend', () => ({ backendFetch: fetchBackend }));
import { GET } from './route';
const item = { id: 1, type: 'POST', title: '제목', category: 'INFO', contentPreview: '소개', author: { id: 1, nickname: '작성자', tag: 'writer', profileImage: null }, createdAt: null, originalPost: null };
const page = { items: [item], page: 0, size: 20, totalElements: 1, totalPages: 1 };
const call = (slug = 'writer', query = '') => GET(new Request('http://localhost/api/profiles/'+slug+'/posts'+query), { params: Promise.resolve({ slug }) });
beforeEach(() => fetchBackend.mockReset());
it('공개 목록을 연결하고 임의 사용자 식별 쿼리는 전달하지 않는다', async () => {
    fetchBackend.mockResolvedValue(Response.json(page));
    const r = await call('writer', '?userId=999');
    expect(r.status).toBe(200); expect(r.headers.get('cache-control')).toBe('no-store');
    expect(fetchBackend).toHaveBeenCalledWith('/api/v1/users/writer/posts?page=0&size=20');
});
it('me 경로는 인증 기반 본인 API로 전달한다', async () => {
    fetchBackend.mockResolvedValue(Response.json(page));
    expect((await call('me')).status).toBe(200);
    expect(fetchBackend).toHaveBeenCalledWith('/api/v1/users/me/posts?page=0&size=20');
});
it('다른 프로필 응답·중복 페이지 쿼리·잘못된 주소를 거절한다', async () => {
    fetchBackend.mockResolvedValue(Response.json(page));
    expect((await call('other')).status).toBe(502);
    fetchBackend.mockClear();
    expect((await call('writer', '?page=0&page=1')).status).toBe(400);
    expect((await call('bad.slug')).status).toBe(400);
    expect(fetchBackend).not.toHaveBeenCalled();
});
it('DB 오류를 빈 목록으로 바꾸거나 내부 정보를 노출하지 않는다', async () => {
    fetchBackend.mockResolvedValue(Response.json({ message: 'internal sql' }, { status: 503 }));
    const r = await call(); expect(r.status).toBe(503); expect(await r.text()).not.toContain('internal sql');
});
