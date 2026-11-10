import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchPortfolioAnalysis, parsePortfolioAnalysis, runPortfolioAnalysis } from './portfolioAnalysis';
const ready = { recommendationId: 7, status: 'READY', cached: false, generatedAt: '2026-11-09T12:00:00',
    portfolios: [{ id: 3, title: '결제', selectionReason: '결제 경험 관련', truncated: false }],
    assessments: [{ requirementId: 'r0', requirement: '결제 경험', status: 'MET', portfolioId: 3, field: 'description', quote: '멱등성 구현' }],
    questions: [], notice: '점수 유지' };
afterEach(() => vi.unstubAllGlobals());
describe('프리미엄 포트폴리오 분석 계약', () => {
    it('허용 필드만 파싱하고 다른 추천·위조 출처·상태 불일치를 거절한다', () => {
        expect(parsePortfolioAnalysis({ ...ready, secret: 'hidden' }, 7)).not.toHaveProperty('secret');
        expect(() => parsePortfolioAnalysis(ready, 8)).toThrow();
        expect(() => parsePortfolioAnalysis({ ...ready, assessments: [{ ...ready.assessments[0], portfolioId: 99 }] }, 7)).toThrow();
        expect(() => parsePortfolioAnalysis({ ...ready, status: 'FAILED' }, 7)).toThrow();
        expect(() => parsePortfolioAnalysis({ ...ready, portfolios: Array(6).fill(ready.portfolios[0]) }, 7)).toThrow();
    });
    it('자료 미확인은 인용과 포트폴리오를 생성하지 않는다', () => {
        const assessment = { ...ready.assessments[0], status: 'UNKNOWN', portfolioId: null, field: null, quote: null };
        expect(parsePortfolioAnalysis({ ...ready, assessments: [assessment] }, 7).assessments[0].quote).toBeNull();
        expect(() => parsePortfolioAnalysis({ ...ready, assessments: [{ ...assessment, quote: '가짜' }] }, 7)).toThrow();
    });
    it('명시 요청으로만 생성하고 no-store를 사용한다', async () => {
        const fetcher = vi.fn().mockResolvedValue(Response.json(ready)); vi.stubGlobal('fetch', fetcher);
        const signal = new AbortController().signal;
        expect((await fetchPortfolioAnalysis(7, 'POST', signal)).status).toBe('READY');
        expect(fetcher).toHaveBeenCalledWith('/api/ai/recommendations/7/detailed-analysis', expect.objectContaining({ method: 'POST', body: '{}', cache: 'no-store', signal }));
    });
    it.each([401, 403, 409, 429, 502, 503, 504])('서버 오류 %s를 성공 결과로 바꾸지 않는다', async status => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ message: '접근 또는 분석 실패' }, { status })));
        await expect(fetchPortfolioAnalysis(7, 'GET', new AbortController().signal)).rejects.toMatchObject({ status });
    });
});

describe('상세 분석은 진행 중에만 자동 조회', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());
    it('READY 후에는 10분이 지나도 추가 요청하지 않는다', async () => {
        const data = parsePortfolioAnalysis(ready, 7);
        const load = vi.fn().mockResolvedValue(data); const receive = vi.fn();
        await runPortfolioAnalysis(7, new AbortController().signal, receive, load);
        await vi.advanceTimersByTimeAsync(600000);
        expect(load).toHaveBeenCalledTimes(1); expect(load.mock.calls[0][1]).toBe('POST');
        expect(receive).toHaveBeenCalledWith(data); expect(vi.getTimerCount()).toBe(0);
    });
    it('PENDING에서만 3초 뒤 GET하고 완료 후 멈춘다', async () => {
        const data = parsePortfolioAnalysis(ready, 7);
        const pending = { ...data, status: 'PENDING' as const, portfolios: [], assessments: [] };
        const load = vi.fn().mockResolvedValueOnce(pending).mockResolvedValueOnce(data);
        const result = runPortfolioAnalysis(7, new AbortController().signal, vi.fn(), load);
        await vi.advanceTimersByTimeAsync(2999); expect(load).toHaveBeenCalledTimes(1);
        await vi.advanceTimersByTimeAsync(1); await result;
        await vi.advanceTimersByTimeAsync(600000);
        expect(load.mock.calls.map(v => v[1])).toEqual(['POST', 'GET']); expect(vi.getTimerCount()).toBe(0);
    });
    it('닫기·계정 변경의 취소 신호는 대기 타이머와 후속 요청을 중단한다', async () => {
        const data = parsePortfolioAnalysis(ready, 7);
        const load = vi.fn().mockResolvedValue({ ...data, status: 'PENDING', portfolios: [], assessments: [] });
        const controller = new AbortController();
        const result = runPortfolioAnalysis(7, controller.signal, vi.fn(), load);
        const rejected = expect(result).rejects.toMatchObject({ name: 'AbortError' });
        await vi.advanceTimersByTimeAsync(1); controller.abort(); await rejected;
        await vi.advanceTimersByTimeAsync(600000);
        expect(load).toHaveBeenCalledTimes(1); expect(vi.getTimerCount()).toBe(0);
    });
    it.each(['NO_DATA', 'FAILED', 'STALE'])('%s 상태를 자동 재생성하지 않는다', async status => {
        const load = vi.fn().mockResolvedValue({ ...parsePortfolioAnalysis(ready, 7), status, portfolios: [], assessments: [] });
        await runPortfolioAnalysis(7, new AbortController().signal, vi.fn(), load);
        await vi.advanceTimersByTimeAsync(600000); expect(load).toHaveBeenCalledTimes(1);
    });
    it('오류가 나도 POST를 자동 재시도하지 않는다', async () => {
        const load = vi.fn().mockRejectedValue(new Error('권한 만료'));
        await expect(runPortfolioAnalysis(7, new AbortController().signal, vi.fn(), load)).rejects.toThrow('권한 만료');
        await vi.advanceTimersByTimeAsync(600000); expect(load).toHaveBeenCalledTimes(1);
    });
});
