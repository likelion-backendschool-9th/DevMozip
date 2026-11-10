import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { changePortfolioVisibility } from './portfolioVisibility';
import { portfolioDetailFixture } from '@/tests/helpers/portfolioApiFixture';
import { PortfolioVisibilityControl } from '@/app/portfolios/_components/PortfolioVisibilityControl';
afterEach(() => vi.unstubAllGlobals());
describe('포트폴리오 공개 범위 요청·화면', () => {
    it('조회한 수정 버전을 유지하고 자동 재시도 없이 PATCH한다', async () => {
        const detail = portfolioDetailFixture(); detail.canEdit = true; detail.portfolio.updatedAt = '2026-11-10T12:00:00';
        const next = structuredClone(detail); next.portfolio.visibility = 'PRIVATE'; next.portfolio.updatedAt = '2026-11-10T12:01:00';
        const fetch = vi.fn().mockResolvedValue(Response.json(next)); vi.stubGlobal('fetch', fetch);
        expect((await changePortfolioVisibility(detail, 'PRIVATE')).portfolio.visibility).toBe('PRIVATE');
        expect(fetch).toHaveBeenCalledTimes(1); const [url, init] = fetch.mock.calls[0];
        expect(url).toBe('/api/portfolios/11/visibility'); expect(init.method).toBe('PATCH');
        expect(JSON.parse(init.body)).toEqual({ visibility: 'PRIVATE', expectedUpdatedAt: '2026-11-10T12:00:00' });
    });
    it('409이면 기존 정보를 변경하거나 자동으로 덮어쓰지 않는다', async () => {
        const detail = portfolioDetailFixture(); detail.canEdit = true;
        const fetch = vi.fn().mockResolvedValue(Response.json({ message: '최신 정보와 연결 상태를 확인해 주세요.' }, { status: 409 })); vi.stubGlobal('fetch', fetch);
        await expect(changePortfolioVisibility(detail, 'PRIVATE')).rejects.toMatchObject({ status: 409 });
        expect(detail.portfolio.visibility).toBe('PUBLIC'); expect(fetch).toHaveBeenCalledTimes(1);
    });
    it('본인에게만 공개 범위 조작을 표시하고 현재 상태에서는 저장을 막는다', () => {
        const detail = portfolioDetailFixture(); const props = { detail, onChanged: () => {}, onReload: () => {} };
        expect(renderToStaticMarkup(<PortfolioVisibilityControl {...props} />)).toBe('');
        detail.canEdit = true;
        const html = renderToStaticMarkup(<PortfolioVisibilityControl {...props} />);
        expect(html).toContain('포트폴리오 공개 범위'); expect(html).toContain('프로젝트에 제출한');
        expect(html).toMatch(/<button[^>]*disabled[^>]*>공개 범위 저장/);
    });
});
