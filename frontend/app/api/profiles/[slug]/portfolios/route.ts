import { parsePortfolioPage, portfolioPagination } from '@/lib/portfolios/portfolioApi';
import { portfolioError, proxyPortfolio } from '@/lib/portfolios/portfolioProxy';
import { validProfileSlug } from '@/lib/profiles/profileDetail';
export async function GET(request: Request, { params }: { params: Promise<{ slug: string }> }) {
    const { slug } = await params;
    if (!validProfileSlug(slug) || slug.toLowerCase() === 'me' && slug !== 'me') return portfolioError(400, '사용자 태그를 확인해 주세요.');
    const pagination = portfolioPagination(new URL(request.url).searchParams);
    if (!pagination) return portfolioError(400, '페이지와 조회 개수를 확인해 주세요.');
    return proxyPortfolio(`/api/v1/users/${encodeURIComponent(slug)}/portfolios?page=${pagination.page}&size=${pagination.size}`, body => {
        const value = parsePortfolioPage(body);
        if (value.page !== pagination.page || value.size !== pagination.size || slug !== 'me' && value.items.some(x => x.visibility !== 'PUBLIC')) throw new Error('목록 응답 불일치');
        return value;
    });
}
