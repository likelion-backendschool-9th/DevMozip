import { parsePortfolioDetail } from '@/lib/portfolios/portfolioApi';
import { portfolioError, proxyPortfolio } from '@/lib/portfolios/portfolioProxy';
import { portfolioWriteGuard, portfolioWriteInput } from '@/lib/portfolios/portfolioWriteProxy';
export async function POST(request: Request) {
    const blocked = portfolioWriteGuard(request); if (blocked) return blocked;
    try {
        const input = await portfolioWriteInput(request);
        let id: number | undefined;
        const response = await proxyPortfolio('/api/v1/portfolios', body => {
            const value = parsePortfolioDetail(body); id = value.portfolio.id; if (!value.canEdit) throw new Error('저장 응답 불일치'); return value;
        }, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) });
        if (response.status === 201 && id) response.headers.set('Location', `/api/portfolios/${id}`);
        return response;
    } catch (error) { return portfolioError(error instanceof RangeError ? 413 : 400, '포트폴리오 입력 형식과 크기를 확인해 주세요.'); }
}
