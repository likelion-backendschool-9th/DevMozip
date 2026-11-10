import { AiApiError } from './aiApi';

export interface AiUsage {
    usageMonth: string;
    usedCount: number;
    limit: number;
    remainingCount: number;
    pendingCount: number;
    availableCount: number;
}
export function parseAiUsage(value: unknown): AiUsage {
    if (!value || typeof value !== 'object') throw new Error('사용량 정보를 확인할 수 없습니다.');
    const v = value as Record<string, unknown>;
    const keys = ['usedCount', 'limit', 'remainingCount', 'pendingCount', 'availableCount'] as const;
    if (typeof v.usageMonth !== 'string' || !/^\d{4}-(0[1-9]|1[0-2])$/.test(v.usageMonth)
        || keys.some(k => !Number.isSafeInteger(v[k]) || (v[k] as number) < 0)) throw new Error('사용량 정보를 확인할 수 없습니다.');
    const result = { usageMonth: v.usageMonth, ...Object.fromEntries(keys.map(k => [k, v[k]])) } as AiUsage;
    if (![5, 100].includes(result.limit) || result.remainingCount !== Math.max(0, result.limit - result.usedCount)
        || result.availableCount !== Math.max(0, result.limit - result.usedCount - result.pendingCount)) throw new Error('사용량 정보를 확인할 수 없습니다.');
    return result;
}
export async function fetchAiUsage(signal: AbortSignal): Promise<AiUsage> {
    const response = await fetch('/api/ai/usage', { cache: 'no-store', signal });
    if (!response.ok) throw new AiApiError(response.status, '사용량을 불러오지 못했습니다. 다시 확인해 주세요.');
    return parseAiUsage(await response.json());
}
