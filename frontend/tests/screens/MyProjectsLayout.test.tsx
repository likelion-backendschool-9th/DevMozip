import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from '@/tests/helpers/RouterFixture';
const mock=vi.hoisted(()=>({resource:vi.fn()}));
vi.mock('@/lib/projects/useProjectApi',()=>({useProjectResource:mock.resource}));
vi.mock('@/lib/state/authStore',()=>({useAuth:()=>({user:{id:'10',email:'layout@example.test',role:'PREMIUM'}})}));
vi.mock('@/lib/auth/actions',()=>({signupOptionsAction:vi.fn()}));
import { ApiProjectList } from '@/app/projects/_components/api/ApiProjectList';
const project={id:41,hostMemberId:10,title:'내 프로젝트 카드',summary:'모집 목록과 같은 카드',description:'소개',imageUrl:'',isPublic:true,recruitmentSlots:[{roleCode:'BACKEND',capacity:2,minimumYears:1}],techStacks:['Java'],interests:['교육'],recruitmentStart:'2026-11-01',recruitmentEnd:'2026-11-30',developmentStart:'2026-12-01',developmentEnd:'2027-01-01',status:'RECRUITING',totalCapacity:3,memberCount:1,viewCount:0,createdAt:'2026-11-01',myRole:'HOST',recruitmentClosed:false};
beforeEach(()=>mock.resource.mockImplementation((path?:string|null)=>({data:path?.startsWith('/mine')?{items:[project],totalElements:1,totalPages:1,page:0}:null,error:null,loading:false})));
describe('내 프로젝트 모집 카드 통일',()=>{
 it('모집 카드 커버·기술·직군을 사용하고 카드 전체가 프로젝트 공통 진입 경로에 연결된다',()=>{
  const html=renderToStaticMarkup(<MemoryRouter initialEntries={['/my-projects?tab=HOSTED&query=React']}><ApiProjectList mine/></MemoryRouter>);
  expect(html).toContain('project-cover');expect(html).toContain('recruitment-card');expect(html).toContain('Java');expect(html).toContain('백엔드');
  expect(html).toContain('/projects/41?returnTo=%2Fmy-projects%3Ftab%3DHOSTED%26query%3DReact');
  expect(html).toContain('cardLink');expect(html).not.toContain('관리하기');expect(html).not.toContain('상세 보기');expect(html).not.toContain('my-project-card');
 });
 it('참여자도 동일한 카드 진입 경로와 목록 복귀 정보를 사용한다',()=>{
  mock.resource.mockImplementation((path?:string|null)=>({data:path?.startsWith('/mine')?{items:[{...project,myRole:'PARTICIPANT'}],totalElements:1,totalPages:1,page:0}:null,error:null,loading:false}));
  const html=renderToStaticMarkup(<MemoryRouter initialEntries={['/my-projects?tab=PARTICIPATING']}><ApiProjectList mine/></MemoryRouter>);
  expect(html).toContain('/projects/41?returnTo=');expect(html).toContain('cardLink');expect(html).not.toContain('참여 정보');
 });
});
