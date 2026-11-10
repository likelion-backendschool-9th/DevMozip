import { parsePortfolioDetail, validPortfolioId } from '@/lib/portfolios/portfolioApi';
import { portfolioError, proxyPortfolio } from '@/lib/portfolios/portfolioProxy';
export async function GET(_request: Request, { params }: { params: Promise<{ id: string }> }) {
    const { id } = await params;
    if (!validPortfolioId(id)) return portfolioError(400, '포트폴리오 ID를 확인해 주세요.');
    return proxyPortfolio(`/api/v1/portfolios/${id}`, body => {
        const value = parsePortfolioDetail(body);
        if (value.portfolio.id !== Number(id)) throw new Error('상세 응답 불일치');
        return value;
    });
}

export async function PUT(request: Request, { params }: { params: Promise<{ id: string }> }) {
    const { id } = await params;
    if (!validPortfolioId(id)) return portfolioError(400, '포트폴리오 ID를 확인해 주세요.');
    const { portfolioWriteGuard, portfolioWriteInput } = await import('@/lib/portfolios/portfolioWriteProxy');
    const blocked = portfolioWriteGuard(request); if (blocked) return blocked;
    try {
        const input = await portfolioWriteInput(request);
        return proxyPortfolio(`/api/v1/portfolios/${id}`, body => {
            const value = parsePortfolioDetail(body);
            if (value.portfolio.id !== Number(id) || !value.canEdit) throw new Error('저장 응답 불일치');
            return value;
        }, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) });
    } catch (error) { return portfolioError(error instanceof RangeError ? 413 : 400, '포트폴리오 입력 형식과 크기를 확인해 주세요.'); }
}
export async function DELETE(request: Request, { params }: { params: Promise<{ id: string }> }) {
    const { id } = await params;
    if (!validPortfolioId(id)) return portfolioError(400, '포트폴리오 ID를 확인해 주세요.');
    const { portfolioWriteGuard } = await import('@/lib/portfolios/portfolioWriteProxy');
    const blocked = portfolioWriteGuard(request, false); if (blocked) return blocked;
    return proxyPortfolio(`/api/v1/portfolios/${id}`, () => { throw new Error('삭제 응답 오류'); }, { method: 'DELETE' });
}
