import type { AiPortfolioAnalysis } from '@/types/aiPortfolioAnalysis';

type Assessment = AiPortfolioAnalysis['assessments'][number];
export interface PortfolioReason {
    portfolio: AiPortfolioAnalysis['portfolios'][number];
    implementations: { quote: string; requirements: string[]; partial: boolean }[];
    skills: string[];
    roles: string[];
    cautions: { quote: string; requirement: string }[];
}
function words(quote: string): string[] {
    try {
        const value: unknown = JSON.parse(quote);
        if (Array.isArray(value) && value.every(v => typeof v === 'string')) return value.map(v => v.trim()).filter(Boolean);
    } catch { /* 인용이 일반 텍스트면 그 텍스트를 그대로 사용한다. */ }
    return [quote.trim()].filter(Boolean);
}
export function portfolioReasons(data: AiPortfolioAnalysis): PortfolioReason[] {
    if (data.status !== 'READY') return [];
    return data.portfolios.flatMap(portfolio => {
        const assessments = data.assessments.filter((a): a is Assessment & { quote: string } =>
            a.portfolioId === portfolio.id && a.status !== 'UNKNOWN' && !!a.quote);
        if (!assessments.length) return [];
        const reason: PortfolioReason = { portfolio, implementations: [], skills: [], roles: [], cautions: [] };
        for (const a of assessments) {
            if (a.status === 'MISMATCH') {
                reason.cautions.push({ quote: a.quote, requirement: a.requirement });
            } else if (a.status === 'MET' && a.field === 'skills') {
                reason.skills.push(...words(a.quote));
            } else if (a.status === 'MET' && a.field === 'role') {
                reason.roles.push(...words(a.quote));
            } else {
                const partial = a.status === 'PARTIAL';
                const existing = reason.implementations.find(v => v.quote === a.quote && v.partial === partial);
                if (existing) { if (!existing.requirements.includes(a.requirement)) existing.requirements.push(a.requirement); }
                else reason.implementations.push({ quote: a.quote, requirements: [a.requirement], partial });
            }
        }
        reason.skills = [...new Set(reason.skills)];
        reason.roles = [...new Set(reason.roles)];
        return [reason];
    });
}
