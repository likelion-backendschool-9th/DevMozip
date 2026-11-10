import type { PortfolioDetail, PortfolioSummary } from '@/types/portfolioApi';
import { parsePortfolioDetail, PortfolioRequestError } from './portfolioApi';
export async function changePortfolioVisibility(detail: PortfolioDetail, visibility: PortfolioSummary['visibility']): Promise<PortfolioDetail> {
    if (!detail.canEdit || !['PUBLIC','PRIVATE'].includes(visibility)) throw new Error('본인 포트폴리오의 공개 범위를 확인해 주세요.');
    const response = await fetch(`/api/portfolios/${detail.portfolio.id}/visibility`, {
        method: 'PATCH', headers: { 'Content-Type': 'application/json' }, cache: 'no-store',
        body: JSON.stringify({ visibility, expectedUpdatedAt: detail.portfolio.updatedAt }),
    });
    if (!response.ok) {
        const error = await response.json().catch(() => null);
        throw new PortfolioRequestError(response.status, error?.message || '공개 범위를 변경하지 못했어요. 최신 내용을 확인해 주세요.');
    }
    const value = parsePortfolioDetail(await response.json());
    if (!value.canEdit || value.portfolio.id !== detail.portfolio.id || value.portfolio.visibility !== visibility) throw new Error('변경 결과를 확인할 수 없어요. 다시 제출하기 전에 새로고침해 주세요.');
    return value;
}
