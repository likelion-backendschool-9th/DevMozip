import { afterEach, describe, expect, it, vi } from 'vitest';
import { portfolioDetailFixture } from '@/tests/helpers/portfolioApiFixture';
import { emptyPortfolio } from '@/mocks/profile';
import { getPortfolioBlocks } from '@/lib/profiles/portfolioBlocks';
import { clearPortfolioDraft } from '@/lib/profiles/portfolioDraft';
import { apiPortfolioErrors, parsePortfolioDocument, portfolioDocument } from './portfolioDocument';
import { apiPortfolioDraftKey, loadApiPortfolioDraft, portfolioInputFromDetail, saveApiPortfolioDraft } from './portfolioApiDraft';
import { deletePortfolio, portfolioWriteBody, savePortfolio } from './portfolioMutations';
import type { PortfolioInput } from '@/types/profile';
const input = (): PortfolioInput => ({ ...structuredClone(emptyPortfolio), title: '프로젝트', summary: '요약', role: '백엔드', stacks: ['Java'], fields: ['AI'],
    start: '2026-01', end: '2026-02', blocks: getPortfolioBlocks(emptyPortfolio), coverImage: 'https://example.com/cover.png' });
afterEach(() => vi.unstubAllGlobals());
describe('문서·쓰기 경계', () => {
    it('자유형 섹션·형식·기간을 보존하며 회원 ID를 보내지 않는다', () => {
        const p = input(); p.blocks![0].format = 'markdown'; p.blocks![0].content = '# 기여\n개발했습니다.';
        const body = portfolioWriteBody(p, 'PRIVATE');
        expect(body.document).toMatchObject({ schema: 'devmozip.portfolio.v1', start: '2026-01', fields: ['AI'] });
        expect(body.document?.blocks[0].format).toBe('markdown'); expect(body).not.toHaveProperty('userId');
    });
    it('서버 상세를 편집 입력으로 되돌린다', () => {
        const d = portfolioDetailFixture(); d.document = portfolioDocument(input());
        expect(portfolioInputFromDetail(d)).toMatchObject({ title: d.portfolio.title, start: '2026-01', fields: ['AI'], blocks: d.document.blocks });
    });
    it.each([
        (p: PortfolioInput) => { p.start = '2026-13'; },
        (p: PortfolioInput) => { p.end = '2025-01'; },
        (p: PortfolioInput) => { p.stacks = ['Java', 'java']; },
        (p: PortfolioInput) => { p.coverImage = 'data:image/png;base64,abc'; },
        (p: PortfolioInput) => { p.blocks = [p.blocks![0], p.blocks![0]]; },
        (p: PortfolioInput) => { p.blocks![0].images = [{ src: 'javascript:alert(1)', caption: '' }]; },
    ])('잘못된 저장 입력을 거절한다', mutate => { const p = input(); mutate(p); expect(apiPortfolioErrors(p).length).toBeGreaterThan(0); });
    it('수정 버전·첨부 메타데이터를 보존한다', () => {
        const existing = portfolioDetailFixture(); existing.portfolio.updatedAt = '2026-11-01T12:00:00';
        const body = portfolioWriteBody(input(), existing.portfolio.visibility, existing);
        expect(body.expectedUpdatedAt).toBe(existing.portfolio.updatedAt); expect(body.attachments).toEqual(existing.attachments);
        expect(() => portfolioWriteBody(input(), 'PRIVATE', existing)).toThrow('공개 범위');
    });
    it('업로드 첨부를 편집 입력에 복원하고 연결 해제를 저장에 반영한다', () => {
        const existing = portfolioDetailFixture();
        const edit = portfolioInputFromDetail(existing);
        expect(edit.attachments).toEqual(existing.attachments);
        expect(portfolioWriteBody({ ...input(), attachments: [] }, 'PUBLIC', existing).attachments).toEqual([]);
        const attachments = [{ name: 'new.pdf', url: '/api/files/12345678-1234-1234-1234-123456789abc' }];
        expect(portfolioWriteBody({ ...input(), attachments }, 'PRIVATE').attachments).toEqual(attachments);
    });
    it('지원하지 않는 문서 버전은 거절한다', () => {
        expect(() => parsePortfolioDocument({ ...portfolioDocument(input()), schema: 'devmozip.portfolio.v99' })).toThrow();
    });
});
describe('쓰기 요청', () => {
    it('등록은 한 번 POST하고 실제 숫자 ID를 받는다', async () => {
        const d = portfolioDetailFixture(); d.canEdit = true;
        const fetcher = vi.fn().mockResolvedValue(Response.json(d, { status: 201 })); vi.stubGlobal('fetch', fetcher);
        expect((await savePortfolio(input(), 'PRIVATE')).portfolio.id).toBe(11);
        expect(fetcher).toHaveBeenCalledTimes(1); expect(fetcher.mock.calls[0][0]).toBe('/api/portfolios'); expect(fetcher.mock.calls[0][1].method).toBe('POST');
    });
    it('수정은 PUT과 원래 버전을 전송한다', async () => {
        const d = portfolioDetailFixture(); d.canEdit = true; d.portfolio.updatedAt = '2026-11-02T12:00:00';
        const fetcher = vi.fn().mockResolvedValue(Response.json(d)); vi.stubGlobal('fetch', fetcher);
        await savePortfolio(input(), 'PUBLIC', d);
        expect(fetcher.mock.calls[0][0]).toBe('/api/portfolios/11'); expect(JSON.parse(fetcher.mock.calls[0][1].body)).toHaveProperty('expectedUpdatedAt', d.portfolio.updatedAt);
    });
    it('충돌·실패를 자동 재전송하지 않는다', async () => {
        const fetcher = vi.fn().mockResolvedValue(Response.json({ message: '충돌' }, { status: 409 })); vi.stubGlobal('fetch', fetcher);
        await expect(savePortfolio(input(), 'PRIVATE')).rejects.toMatchObject({ status: 409 }); expect(fetcher).toHaveBeenCalledTimes(1);
    });
    it('다른 문서 저장 응답은 거절한다', async () => {
        const existing = portfolioDetailFixture(), d = portfolioDetailFixture(); existing.canEdit = d.canEdit = true; d.portfolio.id = 99;
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json(d)));
        await expect(savePortfolio(input(), 'PUBLIC', existing)).rejects.toThrow('저장 결과');
    });
    it('삭제는 204일 때만 성공 처리한다', async () => {
        const fetcher = vi.fn().mockResolvedValueOnce(new Response(null, { status: 204 })).mockResolvedValueOnce(Response.json({ message: '참조 정책 협의 필요' }, { status: 409 })); vi.stubGlobal('fetch', fetcher);
        await expect(deletePortfolio('11')).resolves.toBeUndefined(); await expect(deletePortfolio('11')).rejects.toMatchObject({ status: 409 });
    });
});
describe('실제 계정별 API 초안', () => {
    function storage() { const map = new Map<string, string>(); vi.stubGlobal('localStorage', { getItem: (k: string) => map.get(k) ?? null, setItem: (k: string, v: string) => map.set(k, v), removeItem: (k: string) => map.delete(k) }); return map; }
    it('데모 ID가 같아도 실제 이메일별로 분리한다', () => {
        storage(); const a = apiPortfolioDraftKey('a@test.local'), b = apiPortfolioDraftKey('b@test.local');
        expect(saveApiPortfolioDraft(a, { input: input(), visibility: 'PRIVATE', baseUpdatedAt: null })).toBe(true);
        expect(loadApiPortfolioDraft(a)?.input.title).toBe('프로젝트'); expect(loadApiPortfolioDraft(b)).toBeNull();
        clearPortfolioDraft(a); expect(loadApiPortfolioDraft(a)).toBeNull();
    });
    it('미완성 초안과 원래 수정 버전을 보존한다', () => {
        storage(); const p = input(); p.title = ''; p.start = ''; const key = apiPortfolioDraftKey('a@test.local', '11');
        saveApiPortfolioDraft(key, { input: p, visibility: 'PRIVATE', baseUpdatedAt: '2026-01-01T00:00:00' });
        expect(loadApiPortfolioDraft(key)).toMatchObject({ input: { title: '', start: '' }, baseUpdatedAt: '2026-01-01T00:00:00' });
    });
    it('서버 저장 한도를 넘은 입력도 초안에서는 보존한다', () => {
        storage(); const p = input(); p.blocks = Array.from({ length: 8 }, (_, i) => ({ id: `block-${i}`, kind: 'text', title: '섹션', format: 'text', content: 'x'.repeat(20000) }));
        const key = apiPortfolioDraftKey('a@test.local');
        expect(apiPortfolioErrors(p).length).toBeGreaterThan(0);
        expect(saveApiPortfolioDraft(key, { input: p, visibility: 'PRIVATE', baseUpdatedAt: null })).toBe(true);
        expect(loadApiPortfolioDraft(key)?.input.blocks?.length).toBe(8);
    });
    it('손상된 초안·저장소 실패를 처리한다', () => {
        const map = storage(); map.set('bad', '{broken'); expect(loadApiPortfolioDraft('bad')).toBeNull();
        vi.stubGlobal('localStorage', { setItem: () => { throw new Error('quota'); } }); expect(saveApiPortfolioDraft('x', { input: input(), visibility: 'PRIVATE', baseUpdatedAt: null })).toBe(false);
    });
});
