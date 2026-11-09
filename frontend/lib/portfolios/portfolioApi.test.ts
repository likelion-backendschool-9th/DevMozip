import { afterEach, describe, expect, it, vi } from 'vitest';
import { backendFetch } from '@/lib/auth/backend';
import { GET as list } from '@/app/api/profiles/[slug]/portfolios/route';
import { GET as detail } from '@/app/api/portfolios/[id]/route';
import { fetchPortfolioDetail, fetchPortfolioPage, parsePortfolioDetail, parsePortfolioPage, portfolioDate, portfolioPagination, validPortfolioId } from './portfolioApi';
import { portfolioDetailFixture, portfolioPageFixture } from '@/tests/helpers/portfolioApiFixture';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
afterEach(() => { vi.resetAllMocks(); vi.unstubAllGlobals(); });
const request = (query = '') => new Request(`http://localhost/api/profiles/me/portfolios${query}`);
const params = (slug = 'me') => ({ params: Promise.resolve({ slug }) });
describe('포트폴리오 응답 검증', () => {
    it('제출 상태는 편집 권한보다 우선하고 잘못된 상태를 거절한다', () => {
        const value = { ...portfolioDetailFixture(), canEdit: true, submitted: true };
        expect(parsePortfolioDetail(value)).toMatchObject({ canEdit: false, submitted: true });
        expect(() => parsePortfolioDetail({ ...value, submitted: 'true' })).toThrow();
    });

    it('기본·상세 공개 필드만 보존한다', () => {
        const p = portfolioPageFixture(); Object.assign(p.items[0], { description: 'private', attachments: ['private'] });
        expect(JSON.stringify(parsePortfolioPage(p))).not.toContain('private');
        const d = portfolioDetailFixture(); Object.assign(d.author, { email: 'secret@example.com' }); Object.assign(d.attachments[0], { password: 'secret' });
        expect(JSON.stringify(parsePortfolioDetail(d))).not.toContain('secret');
    });
    it('위험한 이미지·첨부 URL을 전달하지 않는다', () => {
        const d = portfolioDetailFixture(); d.portfolio.thumbnailUrl = 'javascript:alert(1)'; d.author.profileImage = '//evil.test/photo';
        d.attachments.push({ name: '위험', url: 'data:text/html,unsafe' });
        const value = parsePortfolioDetail(d);
        expect(value.portfolio.thumbnailUrl).toBeNull(); expect(value.author.profileImage).toBeNull(); expect(value.attachments).toHaveLength(1);
    });
    it.each([
        (d: ReturnType<typeof portfolioDetailFixture>) => { d.author.id = 99; },
        (d: ReturnType<typeof portfolioDetailFixture>) => { d.portfolio.createdAt = 'yesterday'; },
        (d: ReturnType<typeof portfolioDetailFixture>) => { d.portfolio.techStack = ['Java', 'Java']; },
        (d: ReturnType<typeof portfolioDetailFixture>) => { d.author.tag = '../secret'; },
    ])('불일치 응답을 거절한다', mutate => { const d = portfolioDetailFixture(); mutate(d); expect(() => parsePortfolioDetail(d)).toThrow(); });
    it('빈 목록·NULL을 허용하고 중복 목록은 거절한다', () => {
        expect(parsePortfolioPage({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }).items).toEqual([]);
        const d = portfolioDetailFixture(); d.description = null; d.author.tag = null; d.portfolio.createdAt = null; d.attachments = [];
        expect(parsePortfolioDetail(d).description).toBeNull();
        const p = portfolioPageFixture(); p.items.push(p.items[0]); p.totalElements = 2; expect(() => parsePortfolioPage(p)).toThrow();
    });
    it.each(['page=-1', 'page=01', 'page=1&page=2', 'size=0', 'size=101', 'page=1000001'])('잘못된 페이지 조건 %s', query => {
        expect(portfolioPagination(new URLSearchParams(query))).toBeNull();
    });
    it('안전한 숫자 ID와 한국 날짜를 사용한다', () => {
        expect(validPortfolioId('11')).toBe(true);
        for (const id of ['0', '-1', '01', '1.2', '1e3', '9007199254740993']) expect(validPortfolioId(id)).toBe(false);
        expect(portfolioDate('2026-11-01T15:30:00Z')).toContain('11. 2.');
        expect(portfolioDate('2026-11-01T00:30:00')).toContain('11. 1.');
    });
});
describe('포트폴리오 BFF', () => {
    it('본인 목록은 인증 백엔드 경로로만 전달하고 식별자 쿼리는 무시한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(portfolioPageFixture()));
        const response = await list(request('?userId=99&email=other'), params());
        expect(backendFetch).toHaveBeenCalledWith('/api/v1/users/me/portfolios?page=0&size=20');
        expect(response.status).toBe(200); expect(response.headers.get('Cache-Control')).toBe('no-store');
    });
    it('타인 목록에 PRIVATE가 섞이면 응답하지 않는다', async () => {
        const p = portfolioPageFixture(); p.items[0].visibility = 'PRIVATE';
        vi.mocked(backendFetch).mockResolvedValue(Response.json(p));
        expect((await list(request(), params('api-developer'))).status).toBe(502);
    });
    it('본인 PRIVATE 목록은 허용한다', async () => {
        const p = portfolioPageFixture(); p.items[0].visibility = 'PRIVATE'; vi.mocked(backendFetch).mockResolvedValue(Response.json(p));
        expect((await list(request(), params())).status).toBe(200);
    });
    it.each([400, 401, 403, 404, 503])('백엔드 오류 %i를 보존하고 내부 메시지를 숨긴다', async status => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ detail: 'SELECT secret' }, { status }));
        const response = await detail(request(), { params: Promise.resolve({ id: '11' }) });
        expect(response.status).toBe(status); expect(await response.text()).not.toContain('SELECT');
    });
    it('잘못된 조건은 요청 전에 거절한다', async () => {
        expect((await list(request('?size=0'), params())).status).toBe(400);
        expect((await list(request(), params('ME'))).status).toBe(400);
        expect((await detail(request(), { params: Promise.resolve({ id: '../secrets' }) })).status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('다른 ID·페이지 응답과 서버 연결 실패를 거절한다', async () => {
        vi.mocked(backendFetch).mockResolvedValueOnce(Response.json(portfolioDetailFixture()));
        expect((await detail(request(), { params: Promise.resolve({ id: '12' }) })).status).toBe(502);
        const p = portfolioPageFixture(); p.page = 2; vi.mocked(backendFetch).mockResolvedValueOnce(Response.json(p));
        expect((await list(request(), params())).status).toBe(502);
        vi.mocked(backendFetch).mockRejectedValueOnce(new Error('network secret'));
        expect((await list(request(), params())).status).toBe(502);
    });
});
describe('화면 요청', () => {
    it('503을 빈 목록으로 대체하지 않는다', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ message: '사용 불가' }, { status: 503 })));
        await expect(fetchPortfolioPage('me', 0)).rejects.toMatchObject({ status: 503 });
    });
    it('취소 신호를 전달하고 다른 상세 ID를 거절한다', async () => {
        const fetcher = vi.fn().mockResolvedValue(Response.json(portfolioDetailFixture())); vi.stubGlobal('fetch', fetcher);
        const controller = new AbortController();
        await expect(fetchPortfolioDetail('12', controller.signal)).rejects.toThrow('일치');
        expect(fetcher).toHaveBeenCalledWith('/api/portfolios/12', { cache: 'no-store', signal: controller.signal });
    });
});
