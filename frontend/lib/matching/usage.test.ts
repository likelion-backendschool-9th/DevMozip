import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchAiUsage, parseAiUsage } from './usage';

const usage = { usageMonth: '2026-11', usedCount: 4, limit: 5, remainingCount: 1, pendingCount: 1, availableCount: 0 };
afterEach(() => vi.unstubAllGlobals());
describe('AI 서버 사용량', () => {
    it('처리 중 예약과 남은 횟수를 구분하고 추가 필드를 제외한다', () => {
        expect(parseAiUsage({ ...usage, userId: 'hidden' })).toEqual(usage);
        expect(parseAiUsage({ ...usage, usedCount: 20, remainingCount: 0, pendingCount: 0 })).toMatchObject({ usedCount: 20, limit: 5, availableCount: 0 });
    });
    it.each([
        { ...usage, usedCount: -1 }, { ...usage, usageMonth: '2026-13' }, { ...usage, usedCount: '4' },
        { ...usage, availableCount: 1 }, { ...usage, remainingCount: 2 }, { ...usage, limit: 15 }, null,
    ])('잘못된 서버 응답을 임의 사용량으로 대체하지 않는다: %j', value => {
        expect(() => parseAiUsage(value)).toThrow('사용량 정보를 확인할 수 없습니다.');
    });
    it('현재 계정 사용량을 캐시 없이 조회한다', async () => {
        const fetcher = vi.fn().mockResolvedValue(Response.json(usage)); vi.stubGlobal('fetch', fetcher);
        const signal = new AbortController().signal;
        expect(await fetchAiUsage(signal)).toEqual(usage);
        expect(fetcher).toHaveBeenCalledWith('/api/ai/usage', { cache: 'no-store', signal });
    });
    it('실패를 0회 사용으로 표시하지 않는다', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({}, { status: 401 })));
        await expect(fetchAiUsage(new AbortController().signal)).rejects.toMatchObject({ status: 401 });
    });
});
