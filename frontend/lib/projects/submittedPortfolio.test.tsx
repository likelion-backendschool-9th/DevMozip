import {afterEach,describe,expect,it,vi} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';
import {fetchSubmittedPortfolio,validSubmissionIds} from './submittedPortfolio';
import {PortfolioDetailResult} from '@/app/portfolios/_components/ApiPortfolioDetail';
import {SubmittedPortfolios} from '@/app/projects/_components/api/ApiProjectApplications';
import {portfolioDetailFixture} from '@/tests/helpers/portfolioApiFixture';
import type {ApiApplication} from '@/types/projectApplicationApi';
afterEach(()=>vi.unstubAllGlobals());
describe('지원 포트폴리오 제출본',()=>{
 it('원본 ID 대신 제출본 전용 경로로 요청하고 편집을 허용하지 않는다',async()=>{
  const value={...portfolioDetailFixture(),canEdit:true};const fetcher=vi.fn().mockResolvedValue(Response.json(value));vi.stubGlobal('fetch',fetcher);
  const copy=await fetchSubmittedPortfolio('7','8','9');expect(copy.canEdit).toBe(false);
  expect(fetcher.mock.calls[0][0]).toBe('/api/projects/7/applications/8/portfolios/9');
  const html=renderToStaticMarkup(<PortfolioDetailResult result={{status:'ready',value:copy}} onRetry={()=>{}} backHref="/projects/7" backLabel="프로젝트로 돌아가기"/>);
  expect(html).toContain('프로젝트로 돌아가기');expect(html).not.toContain('/portfolios/11/edit');expect(html).not.toContain('삭제하기');
 });
 it('다른 사람의 제출본과 비정상 응답을 Mock으로 대체하지 않는다',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(Response.json({}, {status:403})));
  await expect(fetchSubmittedPortfolio('7','8','9')).rejects.toMatchObject({status:403});
 });
 it('잘못된 경로는 요청하지 않는다',async()=>{
  const fetcher=vi.fn();vi.stubGlobal('fetch',fetcher);
  expect(validSubmissionIds('7','8','../9')).toBe(false);
  await expect(fetchSubmittedPortfolio('7','08','9')).rejects.toMatchObject({status:400});expect(fetcher).not.toHaveBeenCalled();
 });
 it('원본 연결 ID가 없어져도 제출본 제목과 상세 링크를 유지한다',()=>{
  const application={id:8,projectId:7,portfolioIds:[],submittedPortfolios:[{id:9,sourcePortfolioId:11,title:'지원 당시 제목'}]} as unknown as ApiApplication;
  const html=renderToStaticMarkup(<SubmittedPortfolios application={application}/>);
  expect(html).toContain('제출 포트폴리오 1개');expect(html).toContain('지원 당시 제목');expect(html).toContain('/projects/7/applications/8/portfolios/9');expect(html).not.toContain('href="/portfolios/11"');
 });
});
