import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { PortfolioListResult, type PortfolioListState } from '@/app/(user)/users/[userId]/_components/ApiPortfolioTab';
import { PortfolioDetailResult, type PortfolioDetailState } from '@/app/portfolios/_components/ApiPortfolioDetail';
import { portfolioDetailFixture, portfolioPageFixture } from '@/tests/helpers/portfolioApiFixture';
const list = (result: PortfolioListState) => renderToStaticMarkup(<PortfolioListResult result={result} onRetry={() => {}} onPage={() => {}} />);
const detail = (result: PortfolioDetailState) => renderToStaticMarkup(<PortfolioDetailResult result={result} onRetry={() => {}} />);
describe('API 포트폴리오 화면', () => {
    it('제출된 본인 포트폴리오는 편집 대신 제출 안내와 본인 목록을 표시한다', () => {
        const value = { ...portfolioDetailFixture(), canEdit: false, submitted: true };
        const html = detail({ status: 'ready', value });
        expect(html).toContain('제출 후에는 수정·삭제할 수 없습니다');
        expect(html).toContain('/profile/me?tab=portfolio');
        expect(html).not.toContain('/portfolios/11/edit');
        expect(html).not.toContain('포트폴리오 삭제');
    });

    it('목록의 로딩·실패·빈 결과를 구별한다', () => {
        expect(list({ status: 'loading' })).toContain('role="status"');
        const error = list({ status: 'error', code: 503, message: '조회할 수 없어요.' });
        expect(error).toContain('다시 시도'); expect(error).not.toContain('등록된 포트폴리오가 없습니다');
        expect(list({ status: 'ready', value: { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } })).toContain('아직 등록된 포트폴리오가 없습니다');
    });
    it('기존 카드 형태와 실제 상세 링크·기술 스택을 표시한다', () => {
        const html = list({ status: 'ready', value: portfolioPageFixture() });
        for (const label of ['포트폴리오 제목', '기본 요약', 'Java', '/portfolios/11', '포트폴리오 페이지']) expect(html).toContain(label);
        expect(html).not.toContain('삭제'); expect(html).not.toContain('TOP_SECRET');
    });
    it('상세의 서버 권한 오류를 구별한다', () => {
        expect(detail({ status: 'error', code: 401, message: '로그인 필요' })).toContain('로그인하기');
        expect(detail({ status: 'error', code: 403, message: '권한 필요' })).toContain('/subscription');
        expect(detail({ status: 'error', code: 404, message: '미존재' })).toContain('포트폴리오를 볼 수 없어요');
    });
    it('상세 본문을 안전한 텍스트로 표시하고 Mock 평가·기간을 만들지 않는다', () => {
        const html = detail({ status: 'ready', value: portfolioDetailFixture() });
        for (const label of ['포트폴리오 제목', '프로젝트 설명', '첨부파일', 'https://example.com/design.pdf', '/users/api-developer', '인쇄 / PDF']) expect(html).toContain(label);
        expect(html).toContain('&lt;script&gt;'); expect(html).not.toContain('<script>');
        for (const label of ['AI 피드백', '팀 구성', '진행 기간', '수정</']) expect(html).not.toContain(label);
    });
    it('NULL 태그는 잘못된 공개 프로필 링크로 연결하지 않는다', () => {
        const d = portfolioDetailFixture(); d.author.tag = null;
        expect(detail({ status: 'ready', value: d })).not.toContain('/users/3');
    });
});
