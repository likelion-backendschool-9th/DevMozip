import { parsePortfolioDetail, validPortfolioId } from '@/lib/portfolios/portfolioApi';
import { portfolioError, proxyPortfolio } from '@/lib/portfolios/portfolioProxy';
import { portfolioWriteGuard, portfolioWriteInput } from '@/lib/portfolios/portfolioWriteProxy';

export async function PATCH(request: Request, context: { params: Promise<{ id: string }> }) {
    const blocked = portfolioWriteGuard(request); if (blocked) return blocked;
    const { id } = await context.params;
    if (!validPortfolioId(id)) return portfolioError(400, '포트폴리오 ID를 확인해 주세요.');
    try {
        const raw = await portfolioWriteInput(request);
        const { visibility, expectedUpdatedAt } = raw;
        if (!['PUBLIC','PRIVATE'].includes(visibility as string)
            || !(expectedUpdatedAt === null || typeof expectedUpdatedAt === 'string' && expectedUpdatedAt.length <= 40))
            return portfolioError(400, '공개 범위와 수정 시각을 확인해 주세요.');
        return proxyPortfolio(`/api/v1/portfolios/${id}/visibility`, body => {
            const value = parsePortfolioDetail(body);
            if (!value.canEdit || value.portfolio.id !== Number(id) || value.portfolio.visibility !== visibility) throw new Error('공개 범위 응답 불일치');
            return value;
        }, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ visibility, expectedUpdatedAt }) });
    } catch (e) { return portfolioError(e instanceof RangeError ? 413 : 400, '공개 범위 요청 형식을 확인해 주세요.'); }
}
