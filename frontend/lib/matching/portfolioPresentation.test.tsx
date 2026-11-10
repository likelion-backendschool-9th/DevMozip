import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { PortfolioAnalysisContent } from '@/app/ai/matching/_components/PortfolioAnalysisContent';
import type { AiPortfolioAnalysis } from '@/types/aiPortfolioAnalysis';
import { portfolioReasons } from './portfolioPresentation';
const data: AiPortfolioAnalysis = {
    recommendationId: 7, status: 'READY', cached: true, generatedAt: '2026-11-10T00:00:00',
    portfolios: [{ id: 15, title: '결제 요청 멱등성 API', selectionReason: '사용자 요청', truncated: false },
        { id: 16, title: '근거 없는 다른 자료', selectionReason: '달 착륙선', truncated: false }],
    assessments: [
        { requirementId: 'r0', requirement: '중복 결제 방지 경험', status: 'MET', portfolioId: 15, field: 'description', quote: '멱등성 키로 중복 요청을 처리했습니다.' },
        { requirementId: 'r1', requirement: '재시도 처리 경험', status: 'MET', portfolioId: 15, field: 'description', quote: '멱등성 키로 중복 요청을 처리했습니다.' },
        { requirementId: 'r2', requirement: 'Java', status: 'MET', portfolioId: 15, field: 'skills', quote: '["Java", "Spring Boot"]' },
        { requirementId: 'r3', requirement: 'Spring Boot', status: 'MET', portfolioId: 15, field: 'skills', quote: '["Java", "Spring Boot"]' },
        { requirementId: 'r4', requirement: '달 착륙선 개발', status: 'UNKNOWN', portfolioId: null, field: null, quote: null },
    ], questions: ['달 착륙선 개발을 확인해 주세요.'], notice: '자료 부족·미확인',
};
describe('포트폴리오 추천 이유 문장 표시', () => {
    it('검증된 인용을 개발자 이름·출처 제목과 연결하고 중복 기술은 묶는다', () => {
        const reasons = portfolioReasons(data);
        expect(reasons).toHaveLength(1); expect(reasons[0].implementations).toHaveLength(1);
        expect(reasons[0].skills).toEqual(['Java', 'Spring Boot']);
        const html = renderToStaticMarkup(<PortfolioAnalysisContent data={data} nickname="박민준" />);
        expect(html).toContain('박민준님'); expect(html).toContain('멱등성 키로 중복 요청을 처리했습니다.');
        expect(html).toContain('href="/portfolios/15"'); expect(html).toContain('결제 요청 멱등성 API');
        expect(html).toContain('Java, Spring Boot 기술을 사용했다고');
        expect(html).not.toContain('[&quot;Java&quot;'); expect(html).not.toContain('달 착륙선');
        expect(html).not.toContain('미확인'); expect(html).not.toContain('자료 부족'); expect(html).not.toContain('/portfolios/16');
    });
    it('모든 항목이 미확인이거나 자료가 없으면 빈 분석 목록도 표시하지 않는다', () => {
        const unknown = { ...data, assessments: data.assessments.filter(a => a.status === 'UNKNOWN') };
        expect(renderToStaticMarkup(<PortfolioAnalysisContent data={unknown} nickname="박민준" />)).toBe('');
        expect(renderToStaticMarkup(<PortfolioAnalysisContent data={{ ...data, status: 'NO_DATA' }} />)).toBe('');
    });
    it('구현 중인 부분 근거와 명시적인 차이는 유지하고 완성된 경험으로 바꾸지 않는다', () => {
        const partial: AiPortfolioAnalysis = { ...data, assessments: [
            { ...data.assessments[0], status: 'PARTIAL', quote: '결제 API는 구현 중이며 오류 복구는 아직 개발 중입니다.' },
            { ...data.assessments[1], status: 'MISMATCH', quote: '실제 결제 승인은 구현하지 않았습니다.' },
        ] };
        const html = renderToStaticMarkup(<PortfolioAnalysisContent data={partial} nickname="박민준" />);
        expect(html).toContain('결제 API는 구현 중'); expect(html).toContain('일부 관련');
        expect(html).toContain('현재 완성 상태를 확인'); expect(html).toContain('확인할 차이');
        expect(html).toContain('실제 결제 승인은 구현하지 않았습니다.'); expect(html).not.toContain('미확인');
    });
});
