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
