import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter, Route, Routes } from '@/tests/helpers/RouterFixture';
const mock=vi.hoisted(()=>({resource:vi.fn(),own:true,identity:10}));
vi.mock('@/lib/projects/useProjectApi',()=>({useProjectResource:mock.resource,projectRequest:vi.fn(),ProjectApiError:class extends Error{}}));
vi.mock('@/app/projects/_components/api/ApiProjectApplications',()=>({ApiProjectApplications:({host}:{host:boolean})=><div>{host?'지원자 관리 테스트':'프로젝트 조회 테스트'}</div>}));
vi.mock('@/app/projects/_components/api/ApiProjectReviewStatus',()=>({ApiProjectReviewStatus:()=>null}));
import { ApiProjectDetail } from '@/app/projects/_components/api/ApiProjectDetail';
const project={id:41,hostMemberId:10,title:'역할 자동 진입',summary:'소개',description:'프로젝트 소개',goal:null,imageUrl:'',isPublic:true,recruitmentSlots:[],techStacks:[],interests:[],recruitmentStart:'2026-11-01',recruitmentEnd:'2026-11-30',developmentStart:'2026-12-01',developmentEnd:'2027-01-01',status:'RECRUITING',totalCapacity:3,viewCount:0};
beforeEach(()=>{
 mock.own=true;mock.identity=10;
 mock.resource.mockImplementation((path:string)=>({data:path==='/identity'?{id:mock.identity}:path==='/41'?{project,memberCount:1,recruitmentClosed:false,membership:mock.own?{myRole:'HOST',endRequestCount:0,requiredEndRequestCount:1}:null,host:null}:null,error:null,loading:false,reload:vi.fn()}));
});
const render=(manage=false)=>renderToStaticMarkup(<MemoryRouter initialEntries={['/projects/41']}><Routes><Route path="/projects/:projectId" element={<ApiProjectDetail manage={manage}/>}/></Routes></MemoryRouter>);
describe('프로젝트 주최자 자동 화면',()=>{
 it('기본 상세 경로에서도 실제 주최자는 즉시 관리 기능을 본다',()=>{
  const html=render();expect(html).toContain('지원자 관리 테스트');expect(html).toContain('기간 연장 저장');expect(html).toContain('공고 삭제');expect(html).not.toContain('주최자 관리');
 });
 it('관리 경로로 접근해도 비주최자는 조회 화면만 본다',()=>{
  mock.own=false;mock.identity=11;const html=render(true);
  expect(html).toContain('프로젝트 조회 테스트');expect(html).not.toContain('기간 연장 저장');expect(html).not.toContain('공고 삭제');expect(html).not.toContain('공고 수정');
  expect(mock.resource).toHaveBeenCalledWith('/41');expect(mock.resource).not.toHaveBeenCalledWith('/41/manage');
 });
 it('주최자 멤버 표시와 인증 회원 ID가 다르면 관리 기능을 표시하지 않는다',()=>{
  mock.identity=11;expect(render()).not.toContain('기간 연장 저장');
 });
});
