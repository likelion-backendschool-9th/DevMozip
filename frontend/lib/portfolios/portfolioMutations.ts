import type { PortfolioDetail } from '@/types/portfolioApi';
import type { PortfolioInput } from '@/types/profile';
import { apiPortfolioErrors, portfolioDocument } from './portfolioDocument';
import { parsePortfolioDetail, PortfolioRequestError, validPortfolioId } from './portfolioApi';
export function portfolioWriteBody(input: PortfolioInput, visibility: 'PUBLIC' | 'PRIVATE', existing?: PortfolioDetail) {
    if (existing && visibility !== existing.portfolio.visibility) throw new Error('공개 범위 변경은 별도 기능입니다.');
    const errors = apiPortfolioErrors(input); if (errors.length) throw new Error(errors.join('\n'));
    return { title: input.title, thumbnailUrl: input.coverImage || null, topic: input.fields?.[0] || null, summary: input.summary,
        description: null, role: input.role, techStack: input.stacks, visibility, document: portfolioDocument(input),
        attachments: input.attachments ?? existing?.attachments ?? [], expectedUpdatedAt: existing?.portfolio.updatedAt || null };
}
export async function savePortfolio(input: PortfolioInput, visibility: 'PUBLIC' | 'PRIVATE', existing?: PortfolioDetail, signal?: AbortSignal) {
    const body = JSON.stringify(portfolioWriteBody(input, visibility, existing));
    if (new TextEncoder().encode(body).length > 2 * 1024 * 1024) throw new Error('문서 크기는 2MB 이내여야 합니다.');
    const response = await fetch(existing ? `/api/portfolios/${existing.portfolio.id}` : '/api/portfolios', {
        method: existing ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, cache: 'no-store', body, signal,
    });
    if (!response.ok) { const error = await response.json().catch(() => null); throw new PortfolioRequestError(response.status, error?.message || '저장하지 못했어요. 입력과 초안은 유지됩니다.'); }
    const value = parsePortfolioDetail(await response.json());
    if (!value.canEdit || existing && value.portfolio.id !== existing.portfolio.id) throw new Error('저장 결과를 확인할 수 없어요. 다시 제출하기 전에 내 목록을 확인해 주세요.');
    return value;
}
export async function deletePortfolio(id: string, signal?: AbortSignal) {
    if (!validPortfolioId(id)) throw new Error('포트폴리오 ID를 확인해 주세요.');
    const response = await fetch(`/api/portfolios/${id}`, { method: 'DELETE', cache: 'no-store', signal });
    if (response.status !== 204) { const error = await response.json().catch(() => null); throw new PortfolioRequestError(response.status, error?.message || '삭제하지 못했어요.'); }
}
