import type { AiPortfolioAnalysis } from '@/types/aiPortfolioAnalysis';
import { AiApiError } from './aiApi';

export function parsePortfolioAnalysis(value: unknown, id: number): AiPortfolioAnalysis {
    if (!value || typeof value !== 'object') throw new Error('상세 분석 응답을 확인할 수 없습니다.');
    const v = value as Record<string, unknown>;
    const states = ['READY', 'NO_DATA', 'NOT_GENERATED', 'STALE', 'PENDING', 'FAILED'];
    if (v.recommendationId !== id || !states.includes(String(v.status)) || typeof v.cached !== 'boolean'
        || !(v.generatedAt === null || typeof v.generatedAt === 'string') || typeof v.notice !== 'string'
        || !Array.isArray(v.portfolios) || v.portfolios.length > 5 || !Array.isArray(v.assessments) || v.assessments.length > 200
        || !Array.isArray(v.questions) || !v.questions.every(q => typeof q === 'string')) throw new Error('상세 분석 응답을 확인할 수 없습니다.');
    const portfolios = v.portfolios.map(p => {
        if (!p || !Number.isSafeInteger(p.id) || p.id <= 0 || typeof p.title !== 'string'
            || typeof p.selectionReason !== 'string' || typeof p.truncated !== 'boolean') throw new Error('포트폴리오 출처를 확인할 수 없습니다.');
        return { id: p.id as number, title: p.title as string, selectionReason: p.selectionReason as string, truncated: p.truncated as boolean };
    });
    const ids = new Set(portfolios.map(p => p.id));
    if (ids.size !== portfolios.length) throw new Error('포트폴리오 출처가 중복되었습니다.');
    const seen = new Set<string>();
    const assessments = v.assessments.map(a => {
        if (!a || typeof a.requirementId !== 'string' || seen.has(a.requirementId) || typeof a.requirement !== 'string'
            || !['MET', 'PARTIAL', 'MISMATCH', 'UNKNOWN'].includes(a.status)
            || !(a.portfolioId === null || Number.isSafeInteger(a.portfolioId) && ids.has(a.portfolioId))
            || !(a.field === null || ['title', 'summary', 'role', 'skills', 'description'].includes(a.field))
            || !(a.quote === null || typeof a.quote === 'string' && a.quote.length > 0 && a.quote.length <= 500)) throw new Error('분석 근거를 확인할 수 없습니다.');
        if (a.status === 'UNKNOWN' ? a.portfolioId !== null || a.field !== null || a.quote !== null : a.portfolioId === null || a.field === null || a.quote === null)
            throw new Error('분석 근거가 누락되었습니다.');
        seen.add(a.requirementId);
        return { requirementId: a.requirementId, requirement: a.requirement, status: a.status, portfolioId: a.portfolioId, field: a.field, quote: a.quote };
    });
    if (v.status !== 'READY' && (assessments.length || portfolios.length || v.questions.length)) throw new Error('분석 상태와 자료가 일치하지 않습니다.');
    return { recommendationId: id, status: v.status as AiPortfolioAnalysis['status'], cached: v.cached, generatedAt: v.generatedAt as string | null,
        portfolios, assessments, questions: v.questions as string[], notice: v.notice };
}
export async function fetchPortfolioAnalysis(id: number, method: 'GET' | 'POST', signal: AbortSignal): Promise<AiPortfolioAnalysis> {
    if (!Number.isSafeInteger(id) || id <= 0) throw new AiApiError(400, '추천 결과 ID를 확인해 주세요.');
    const response = await fetch(`/api/ai/recommendations/${id}/detailed-analysis`, { method, cache: 'no-store', signal,
        ...(method === 'POST' ? { headers: { 'Content-Type': 'application/json' }, body: '{}' } : {}) });
    const body = await response.json();
    if (!response.ok) throw new AiApiError(response.status, typeof body?.message === 'string' ? body.message : '상세 분석 요청에 실패했습니다.');
    return parsePortfolioAnalysis(body, id);
}

function waitForPending(signal: AbortSignal): Promise<void> {
    return new Promise((resolve, reject) => {
        const abort = () => { clearTimeout(timer); reject(new DOMException('분석 조회를 중단했습니다.', 'AbortError')); };
        const timer = setTimeout(() => { signal.removeEventListener('abort', abort); resolve(); }, 3000);
        if (signal.aborted) abort();
        else signal.addEventListener('abort', abort, { once: true });
    });
}

/** 생성 중에만 조회한다. 완료·자료 없음·오류 후에는 타이머나 자동 재생성이 없다. */
export async function runPortfolioAnalysis(id: number, signal: AbortSignal, onResult: (data: AiPortfolioAnalysis) => void,
    load: typeof fetchPortfolioAnalysis = fetchPortfolioAnalysis): Promise<void> {
    let method: 'GET' | 'POST' = 'POST';
    for (let attempt = 0; attempt <= 60; attempt++) {
        if (signal.aborted) return;
        const data = await load(id, method, signal);
        if (signal.aborted) return;
        onResult(data);
        if (data.status !== 'PENDING') return;
        if (attempt === 60) throw new AiApiError(504, '분석 처리가 지연되고 있습니다. 잠시 후 다시 확인해 주세요.');
        await waitForPending(signal);
        method = 'GET';
    }
}
