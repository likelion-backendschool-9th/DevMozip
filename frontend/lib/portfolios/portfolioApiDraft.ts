import type { PortfolioDetail } from '@/types/portfolioApi';
import type { PortfolioInput } from '@/types/profile';
import { parsePortfolioDocument } from './portfolioDocument';
export const apiPortfolioDraftKey = (email: string, id?: string) => `devmozip:portfolio-api-draft:v1:${encodeURIComponent(email)}:${id || 'new'}`;
export interface ApiPortfolioDraft { input: PortfolioInput; visibility: 'PUBLIC' | 'PRIVATE'; baseUpdatedAt: string | null }
export function portfolioInputFromDetail(value: PortfolioDetail): PortfolioInput {
    const p = value.portfolio, d = value.document;
    return { attachments: value.attachments.map(a => ({ ...a })), title: p.title || '', summary: p.summary || '', role: p.role || '', stacks: [...p.techStack], coverImage: p.thumbnailUrl || '',
        field: p.topic || '', fields: d?.fields || (p.topic ? [p.topic] : []), start: d?.start || '', end: d?.end || '', teamSize: d?.teamSize || '',
        description: value.description || '', features: '', challenge: '', outcome: '',
        blocks: d?.blocks || [{ id: 'description', kind: 'text', title: '프로젝트 설명', content: value.description || '', format: 'text', source: 'description' }] };
}
export function loadApiPortfolioDraft(key: string): ApiPortfolioDraft | null {
    try {
        const raw: unknown = JSON.parse(localStorage.getItem(key) || 'null');
        if (!raw || typeof raw !== 'object') return null;
        const v = raw as Record<string, unknown>, p = v.input as Record<string, unknown> | undefined;
        if (v.version !== 1 || !p || !['PUBLIC', 'PRIVATE'].includes(v.visibility as string) || !(v.baseUpdatedAt === null || typeof v.baseUpdatedAt === 'string')
            || !['title', 'summary', 'start', 'end', 'role', 'description', 'features', 'challenge', 'outcome'].every(k => typeof p[k] === 'string')
            || !(p.coverImage === undefined || typeof p.coverImage === 'string') || !Array.isArray(p.stacks) || p.stacks.length > 30 || !p.stacks.every(s => typeof s === 'string' && s.length <= 100)) return null;
        if (p.attachments !== undefined && (!Array.isArray(p.attachments) || p.attachments.length > 100 || p.attachments.some(a => !a || typeof a !== 'object' || typeof a.url !== 'string' || a.url.length > 2048 || !(a.name === null || typeof a.name === 'string' && a.name.length <= 255)))) return null;
        const d = parsePortfolioDocument({ schema: 'devmozip.portfolio.v1', start: p.start, end: p.end, teamSize: p.teamSize || null, fields: p.fields || [], blocks: p.blocks }, true);
        return { input: { ...(p as unknown as PortfolioInput), fields: d.fields, blocks: d.blocks }, visibility: v.visibility as 'PUBLIC' | 'PRIVATE', baseUpdatedAt: v.baseUpdatedAt as string | null };
    } catch { return null; }
}
export function saveApiPortfolioDraft(key: string, draft: ApiPortfolioDraft) {
    try { const raw = JSON.stringify({ version: 1, ...draft }); if (new TextEncoder().encode(raw).length > 2 * 1024 * 1024) return false; localStorage.setItem(key, raw); return true; } catch { return false; }
}
