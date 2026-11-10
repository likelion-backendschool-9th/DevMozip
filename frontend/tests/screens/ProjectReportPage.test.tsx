import {afterEach,expect,it,vi} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';
import {MemoryRouter,Route,Routes} from '@/tests/helpers/RouterFixture';
import Page from '@/app/projects/[projectId]/report/page';
import {resetLocalStores} from '@/lib/localStore';
import {signIn} from '@/tests/helpers/session';
import {PROJECT_KEY,applyRecruitmentAction,freshRecruitmentState} from '@/lib/projects/projectService';
import {freshProfileState} from '@/lib/profiles/profileService';
import {demoAccounts} from '@/mocks/auth';
function page(userId?:string,reported=false){let state=freshRecruitmentState();if(reported)state=applyRecruitmentAction(state,{type:'report',projectId:'pocket',reason:'기타',detail:''},demoAccounts[0],freshProfileState()).state;vi.stubGlobal('localStorage',{getItem:(key:string)=>key===PROJECT_KEY?JSON.stringify(state):null});vi.stubGlobal('sessionStorage',{getItem:()=>null});resetLocalStores();signIn(userId);return renderToStaticMarkup(<MemoryRouter initialEntries={['/projects/pocket/report']}><Routes><Route path="/projects/:projectId/report" element={<Page/>}/></Routes></MemoryRouter>);}
afterEach(()=>vi.unstubAllGlobals());
it('별도 신고 페이지에서 대상 공고와 사유·선택 설명을 표시한다',()=>{const html=page('demo-user');for(const text of ['프로젝트 신고','Pocket','허위 모집 정보','추가 설명','신고 접수'])expect(html).toContain(text);expect(html).toContain('type="radio"');expect(html).not.toContain('사용자 신고');});
it('비로그인·본인 공고·이미 신고한 공고의 제출을 제한한다',()=>{expect(page()).toContain('/login?next=');expect(page('demo-premium')).toContain('본인의 프로젝트는 신고할 수 없습니다');expect(page('demo-user',true)).toContain('이미 신고한 프로젝트');});
