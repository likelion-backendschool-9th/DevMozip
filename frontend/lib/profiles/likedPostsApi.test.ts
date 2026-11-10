import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchLikedPosts, likedPostCategoryLabel, likedPostsPagination, parseLikedPostsPage } from './likedPostsApi';
import { GET } from '@/app/api/profiles/me/liked-posts/route';
import { backendFetch } from '@/lib/auth/backend';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
const fixture = () => ({ page: 0, size: 20, totalElements: 1, totalPages: 1, items: [{ id: 12, type: 'POST', category: null,
    contentPreview: '<script>unsafe</script>', author: { id: 3, nickname: '개발자', tag: null, profileImage: null, email: 'private' },
    createdAt: '2026-11-01T12:00:00+09:00', likedAt: null, originalPost: null, password: 'secret' }] });
afterEach(() => { vi.unstubAllGlobals(); vi.resetAllMocks(); });
describe('좋아요 목록 응답 경계', () => {
    it('공개 필드만 보존하며 태그와 날짜의 NULL을 허용한다', () => {
        const value = parseLikedPostsPage(fixture());
        expect(value.items[0].author.tag).toBeNull();
        expect(JSON.stringify(value)).not.toMatch(/private|secret|email|password/);
    });
    it.each([
        (p: ReturnType<typeof fixture>) => { p.items.push(p.items[0]); p.totalElements = 2; },
        (p: ReturnType<typeof fixture>) => { p.items[0].type = 'REPOST'; },
        (p: ReturnType<typeof fixture>) => { p.items[0].createdAt = '2026-11-01T12:00:00'; },
        (p: ReturnType<typeof fixture>) => { p.totalPages = 3; },
        (p: ReturnType<typeof fixture>) => { p.items[0].contentPreview = 'x'.repeat(601); },
    ])('모순되거나 안전하지 않은 응답을 거절한다', mutate => { const p = fixture(); mutate(p); expect(() => parseLikedPostsPage(p)).toThrow(); });
    it.each(['page=-1', 'page=01', 'size=0', 'size=101', 'page=1&page=2', 'page=1000001', 'size=NaN'])('잘못된 페이지 조건 %s', query => {
        expect(likedPostsPagination(new URLSearchParams(query))).toBeNull();
    });
    it('기본값과 허용 경계를 사용한다', () => {
        expect(likedPostsPagination(new URLSearchParams())).toEqual({ page: 0, size: 20 });
        expect(likedPostsPagination(new URLSearchParams('page=1000000&size=100'))).toEqual({ page: 1000000, size: 100 });
    });
    it('조회 실패를 빈 목록으로 바꾸지 않는다', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ message: '잠시 후 다시 시도' }, { status: 503 })));
        await expect(fetchLikedPosts(0, 20)).rejects.toMatchObject({ status: 503 });
    });
    it('요청 페이지와 다른 응답을 거절하고 취소 신호를 전달한다', async () => {
        const fetcher = vi.fn().mockResolvedValue(Response.json(fixture())); vi.stubGlobal('fetch', fetcher);
        const controller = new AbortController();
        await expect(fetchLikedPosts(1, 20, controller.signal)).rejects.toThrow('일치');
        expect(fetcher).toHaveBeenCalledWith('/api/profiles/me/liked-posts?page=1&size=20', { cache: 'no-store', signal: controller.signal });
    });
});
describe('좋아요 목록 BFF', () => {
    it('회원 식별자를 전달하지 않고 인증된 백엔드 조회를 사용한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(fixture()));
        const response = await GET(new Request('http://localhost/api/profiles/me/liked-posts?userId=999&email=other'));
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/users/me/liked-posts?page=0&size=20');
        expect(response.headers.get('Cache-Control')).toBe('no-store');
        expect(JSON.stringify(await response.json())).not.toMatch(/private|secret/);
    });
    it.each([400, 401, 403, 404, 503])('오류 %i를 보존하고 내부 오류 내용은 전달하지 않는다', async status => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ detail: 'SELECT secret FROM users' }, { status }));
        const response = await GET(new Request('http://localhost/api/profiles/me/liked-posts'));
        expect(response.status).toBe(status); expect(await response.text()).not.toContain('SELECT');
    });
    it('잘못된 쿼리는 백엔드를 호출하지 않는다', async () => {
        expect((await GET(new Request('http://localhost/api/profiles/me/liked-posts?size=0'))).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('연결 실패와 응답 형식 오류는 502다', async () => {
        vi.mocked(backendFetch).mockRejectedValueOnce(new Error('connection secret'));
        expect((await GET(new Request('http://localhost/api/profiles/me/liked-posts'))).status).toBe(502);
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json({ items: [] }));
        expect((await GET(new Request('http://localhost/api/profiles/me/liked-posts'))).status).toBe(502);
    });
});

it.each([['INFO', '정보'], ['FEEDBACK', '피드백'], ['PROMO', '홍보'], ['COMMUNITY', '자유'], [null, '분류 없음'], ['기존 분류', '기존 분류']])('카테고리 %s를 화면 이름으로 표시한다', (value, label) => {
    expect(likedPostCategoryLabel(value)).toBe(label);
});
