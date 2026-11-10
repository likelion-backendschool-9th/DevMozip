import { resetLocalStores } from "@/lib/localStore";
import { afterEach,beforeEach,describe,expect,it,vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter,Route,Routes } from '@/tests/helpers/RouterFixture';
import { AppLayout } from '@/app/_components/AppLayout';
import ProjectsPage from '@/app/projects/page';
import ProjectDetailPage from '@/app/projects/[projectId]/page';
import CreateProjectPage from '@/app/projects/new/_components/CreateProjectPage';
import { ApplicationModal,CancelApplicationModal } from '@/app/projects/[projectId]/_components/ApplicationModal';
import { signIn } from '@/tests/helpers/session';
import { PROJECT_KEY,allProjects,applyRecruitmentAction,freshRecruitmentState } from '@/lib/projects/projectService';
import { freshProfileState } from '@/lib/profiles/profileService';
import { demoAccounts } from '@/mocks/auth';
import type { RecruitmentState } from '@/types/project';
const now=new Date('2026-10-30T12:00:00+09:00');
function render(url:string,userId?:string,state=freshRecruitmentState(),modal?:'apply'|'cancel',apiEnabled=false){
 const data=new Map<string,string>();data.set(PROJECT_KEY,JSON.stringify(state));vi.stubGlobal('localStorage',{getItem:(key:string)=>data.get(key)||null});vi.stubGlobal('sessionStorage',{getItem:()=>null});const project=allProjects(state).find(p=>p.id==='pocket')!;
 return resetLocalStores(),signIn(userId),renderToStaticMarkup(<><MemoryRouter initialEntries={[url]}>{modal?(modal==='apply'?<ApplicationModal project={project} onClose={()=>{}}/>:<CancelApplicationModal project={project} onClose={()=>{}}/>):<Routes><Route element={<AppLayout/>}><Route path="/projects" element={<ProjectsPage/>}/><Route path="/projects/new" element={<CreateProjectPage apiEnabled={apiEnabled}/>}/><Route path="/projects/:projectId" element={<ProjectDetailPage/>}/></Route></Routes>}</MemoryRouter></>);
}
function appliedState():RecruitmentState{return applyRecruitmentAction(freshRecruitmentState(),{type:'apply',projectId:'pocket',portfolioId:'pf-haneul',role:'백엔드',message:'협업 경험을 바탕으로 함께 멋진 서비스를 만들고 싶습니다.'},demoAccounts[0],freshProfileState(),now).state;}
beforeEach(()=>{vi.useFakeTimers();vi.setSystemTime(now);});afterEach(()=>{vi.unstubAllGlobals();vi.useRealTimers();});
describe('모집 목록·상세·지원 화면',()=>{
 it('목록 검색/필터 결과에 직군별 인원과 경력을 표시한다',()=>{const html=render('/projects?q=Pocket&role=백엔드&sort=popular');expect(html).toContain('/projects/pocket');expect(html).toContain('1년 이상');expect(html).toContain('1/2명');expect(html).not.toContain('/projects/flowmate');});
 it('상세에 소개/목표/기간/요구 조건과 주최자 프로필을 표시한다',()=>{const html=render('/projects/pocket');for(const text of ['프로젝트 소개','함께 이루고 싶은 목표','모집 직군과 인원','최소 경력','개발 기간','모집 기간','프로젝트 주최자'])expect(html).toContain(text);expect(html).toContain('/users/demo-premium');expect(html).toContain('주최자에게 쪽지');});
 it('무료 회원은 지원 가능하고 비로그인은 로그인 안내를 표시한다',()=>{expect(render('/projects/pocket','demo-user')).toContain('프로젝트 지원하기');expect(render('/projects/pocket')).toContain('로그인하고 지원하기');});
 it('종료된 프로젝트는 지원이 비활성화된다',()=>{const html=render('/projects/studybridge','demo-premium');expect(html).toContain('현재 지원을 받지 않는 프로젝트입니다.');expect(html).toContain('disabled=""');});
 it('저장된 북마크 상태를 상세와 목록에서 표시한다',()=>{const state=applyRecruitmentAction(freshRecruitmentState(),{type:'bookmark',projectId:'pocket'},demoAccounts[0],freshProfileState(),now).state;expect(render('/projects/pocket','demo-user',state)).toContain('북마크 해제');expect(render('/projects?q=Pocket','demo-user',state)).toContain('aria-pressed="true"');});
 it('지원 완료 상태와 취소 버튼을 상세 및 목록에서 복원한다',()=>{const state=appliedState();const html=render('/projects/pocket','demo-user',state);expect(html).toContain('지원 완료');expect(html).toContain('지원 취소');expect(html).toContain('DevLog · 개발자의 꾸준한 기록');expect(render('/projects?q=Pocket','demo-user',state)).toContain('지원 완료');});
 it('취소 상태에서는 다시 지원할 수 있다',()=>{const state=applyRecruitmentAction(appliedState(),{type:'cancel',projectId:'pocket',acknowledgePenalty:true},demoAccounts[0],freshProfileState(),now).state;expect(render('/projects/pocket','demo-user',state)).toContain('다시 지원하기');});
 it('지원 모달에는 본인 포트폴리오만 표시한다',()=>{const html=render('/projects/pocket','demo-user',freshRecruitmentState(),'apply');expect(html).toContain('DevLog · 개발자의 꾸준한 기록');expect(html).not.toContain('Flow UI');expect(html).toContain('지원 멘트');});
 it('임박 취소 모달은 임시 수치와 미차감 안내, 확인 체크를 제공한다',()=>{const html=render('/projects/pocket','demo-user',appliedState(),'cancel');expect(html).toContain('−0.5°C');expect(html).toContain('온도를 차감하지 않습니다');expect(html).toContain('임시 패널티 안내를 확인했습니다');expect(html).toContain('disabled=""');});
 it('작성 경로에서 일반 회원은 제한되고 프리미엄은 폼을 표시한다',()=>{expect(render('/projects/new','demo-user')).toContain('모집 공고 작성은 프리미엄 전용입니다.');const html=render('/projects/new','demo-premium');expect(html).toContain('모집 공고 등록');expect(html).toContain('모집 직군별 인원 및 최소 경력');expect(html).not.toContain('프로젝트를 찾을 수 없어요.');});
 it('API 모드에서만 대표 이미지와 공개 범위 입력을 표시한다',()=>{const apiHtml=render('/projects/new','demo-premium',freshRecruitmentState(),undefined,true);expect(apiHtml).toContain('대표 이미지 URL');expect(apiHtml).toContain('팀원만 공개');expect(render('/projects/new','demo-premium')).not.toContain('대표 이미지 URL');});
 it('존재하지 않는 프로젝트와 빈 검색 결과를 안내한다',()=>{expect(render('/projects/missing')).toContain('프로젝트를 찾을 수 없어요.');expect(render('/projects?q=없는프로젝트')).toContain('조건에 맞는 프로젝트가 없습니다.');});
});

describe('Mock 프로젝트 역할 자동 진입',()=>{it('주최자는 상세에서도 관리 화면을 보고 일반 사용자는 조회 화면을 본다',()=>{expect(render('/projects/pocket','demo-premium')).toContain('지원자 목록');expect(render('/projects/pocket','demo-premium')).not.toMatch(/<a[^>]*>\s*주최자 관리\s*<\/a>/);expect(render('/projects/pocket','demo-user')).not.toContain('지원자 목록');});});
