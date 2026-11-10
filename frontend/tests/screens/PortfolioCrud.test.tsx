import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ApiPortfolioComposer } from '@/app/portfolios/_components/ApiPortfolioComposer';
import { PortfolioEditorForm } from '@/app/portfolios/_components/PortfolioComposer';
import { PortfolioDetailResult } from '@/app/portfolios/_components/ApiPortfolioDetail';
import { PortfolioListResult } from '@/app/(user)/users/[userId]/_components/ApiPortfolioTab';
import { portfolioDetailFixture, portfolioPageFixture } from '@/tests/helpers/portfolioApiFixture';
import { emptyPortfolio } from '@/mocks/profile';
import { getPortfolioBlocks } from '@/lib/profiles/portfolioBlocks';
import { portfolioDocument } from '@/lib/portfolios/portfolioDocument';
import { syncServerSession } from '@/lib/state/authStore';
import { resetLocalStores } from '@/lib/localStore';
const input = () => ({ ...structuredClone(emptyPortfolio), title: '프로젝트', summary: '요약', role: '개발', stacks: ['Java'], start: '2026-01', end: '2026-02', fields: ['AI'], blocks: getPortfolioBlocks(emptyPortfolio) });
beforeEach(() => resetLocalStores()); afterEach(() => { resetLocalStores(); vi.unstubAllGlobals(); });
describe('포트폴리오 작성·수정·삭제 화면', () => {
    it('비로그인은 작성 화면에서 로그인 안내를 받는다', () => {
        syncServerSession({ identity: null });
        expect(renderToStaticMarkup(<ApiPortfolioComposer />)).toContain('로그인하고 포트폴리오를 작성하세요');
    });
    it('기존 작성기 배치·미리보기·섹션을 유지하고 업로드만 비활성화한다', () => {
        syncServerSession({ identity: { username: 'real@test.local', role: 'USER' } });
        const html = renderToStaticMarkup(<ApiPortfolioComposer />);
        for (const label of ['포트폴리오 작성', '전체 미리보기', '프로젝트명', '한 줄 소개', '시작 월', '종료 월', '담당 역할', '팀 구성', '공개 범위', '대표 이미지 URL', '자유롭게 구성하는 본문']) expect(html).toContain(label);
        expect(html).toContain('href="/profile/me?tab=portfolio"');
        expect(html).toMatch(/<input[^>]*aria-label="대표 이미지"[^>]*disabled/);
    });
    it('실제 수정 대상은 조회 완료 전 Mock 내용을 표시하지 않는다', () => {
        syncServerSession({ identity: { username: 'real@test.local', role: 'USER' } });
        const html = renderToStaticMarkup(<ApiPortfolioComposer id="11" />);
        expect(html).toContain('포트폴리오를 불러오는 중'); expect(html).not.toContain('DevLog');
    });
    it('본인에게만 목록의 수정·삭제를 표시한다', () => {
        const base = { result: { status: 'ready' as const, value: portfolioPageFixture() }, onRetry: () => {}, onPage: () => {} };
        expect(renderToStaticMarkup(<PortfolioListResult {...base} own />)).toContain('/portfolios/11/edit');
        expect(renderToStaticMarkup(<PortfolioListResult {...base} own />)).toContain('삭제');
        expect(renderToStaticMarkup(<PortfolioListResult {...base} />)).not.toContain('/edit');
    });
    it('상세에서 자유형 문서·기간을 복원하고 HTML을 정제한다', () => {
        const p = input(); p.blocks[0].format = 'html'; p.blocks[0].content = '<p>내 기여</p><script>alert(1)</script>';
        const value = portfolioDetailFixture(); value.document = portfolioDocument(p); value.canEdit = true;
        const html = renderToStaticMarkup(<PortfolioDetailResult result={{ status: 'ready', value }} onRetry={() => {}} />);
        expect(html).toContain('2026-01 — 2026-02'); expect(html).toContain('내 기여'); expect(html).not.toContain('<script>'); expect(html).toContain('/portfolios/11/edit');
    });
    it('오래된 초안은 숨기거나 지우지 않고 최신 내용 복구 동작을 제공한다', () => {
        const html = renderToStaticMarkup(<PortfolioEditorForm userId="real@test.local" api={{
            initial: input(), draftKey: 'draft', visibility: 'PRIVATE', canChangeVisibility: false, staleDraft: true,
            save: async () => '/portfolios/11', saveDraft: () => true, reload: () => {}, backHref: '/portfolios/11',
        }} />);
        expect(html).toContain('초안 작성 후 서버 내용이 변경'); expect(html).toContain('초안 버리고 최신 내용 불러오기'); expect(html).toContain('value="프로젝트"');
    });
});
