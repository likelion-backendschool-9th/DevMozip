import { resetLocalStores } from "@/lib/localStore";
import { buildChatResult,newMatchingConversation,sendMatchingMessage } from '@/lib/matching/matchingChatService';
import { afterEach,beforeEach,describe,expect,it,vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter,Route,Routes } from '@/tests/helpers/RouterFixture';
import { AppLayout } from '@/app/_components/AppLayout';
import FollowingPage from '@/app/(user)/users/[userId]/follows/page';
import { AccountLayout } from '@/app/(user)/_components/AccountLayout';
import MatchingPage from '@/app/ai/matching/page';
import HomePage from '@/app/page';
import ProjectNotificationsPage from '@/app/notifications/page';
import { FollowSettings } from '@/app/(user)/users/[userId]/_components/FollowSettings';
import { signIn } from '@/tests/helpers/session';
import { HOME_KEY,freshHomeState } from '@/lib/matching/homeService';
import { PROFILE_KEY,applyProfileAction,freshProfileState } from '@/lib/profiles/profileService';
import { demoAccounts } from '@/mocks/auth';
import { developers,projects } from '@/mocks/home';
import { emptyPost } from '@/mocks/social';
import type { HomeState } from '@/types/home';
import type { ProfileState } from '@/types/profile';
const now=new Date('2026-10-30T12:00:00+09:00');
// 기존에 저장된 Mock 결과의 조회 회귀용 fixture. 전송 동작으로 새 추천을 만들지 않는다.
function matched(user=demoAccounts[0]){const state=freshHomeState();const requirements={description:'React 일정 관리 웹 서비스를 만들고 싶어요.',fields:['웹'],interests:['생산성'],stacks:['React'],roles:[{name:'프론트엔드',count:2}],career:'경력 무관',duration:'3개월'};const conversation=sendMatchingMessage(newMatchingConversation(now),'추천해 주세요',requirements,now);conversation.messages.push({id:'legacy-result',role:'assistant',text:'이전 Mock 추천 결과',createdAt:now.toISOString(),result:buildChatResult(requirements,developers,projects)});state.matching.usage[user.id]={'2026-10':1};state.chat={conversations:{[user.id]:[conversation]},active:{[user.id]:conversation.id}};return state;}
function page(url:string,userId?:string,home:HomeState=freshHomeState(),profiles:ProfileState=freshProfileState()){const data=new Map<string,string>();data.set(HOME_KEY,JSON.stringify(home));data.set(PROFILE_KEY,JSON.stringify(profiles));vi.stubGlobal('localStorage',{getItem:(key:string)=>data.get(key)||null});vi.stubGlobal('sessionStorage',{getItem:()=>null});return resetLocalStores(),signIn(userId),renderToStaticMarkup(<><MemoryRouter initialEntries={[url]}><Routes><Route element={<AppLayout/>}><Route element={<AccountLayout>{null}</AccountLayout>}><Route path="/users/:userId/follows" element={<FollowingPage/>}/></Route><Route path="/ai/matching" element={<MatchingPage/>}/><Route path="/" element={<HomePage/>}/><Route path="/notifications" element={<ProjectNotificationsPage/>}/><Route path="/settings" element={<FollowSettings targetId="yuna" onClose={()=>{}}/>}/></Route></Routes></MemoryRouter></>);}
beforeEach(()=>{vi.useFakeTimers();vi.setSystemTime(now);});afterEach(()=>{vi.unstubAllGlobals();vi.useRealTimers();});
describe('팔로우·추천 화면 렌더링',()=>{
 it('팔로잉 검색에 프로필·협업 상태·알림 설정을 표시한다',()=>{const html=page('/users/demo-user/follows?q=유나','demo-user');expect(html).toContain('이유나');expect(html).not.toContain('박민준');expect(html).toContain('/users/yuna');expect(html).toContain('협업 가능');expect(html).toContain('언팔로우');expect(html).toContain('이유나 알림 설정');});
 it('팔로워 탭과 공개 사용자 목록을 조회할 수 있다',()=>{const html=page('/users/yuna/follows?tab=followers','demo-user');expect(html).toContain('김하늘');expect(html).toContain('팔로워');expect(page('/users/missing/follows','demo-user')).toContain('사용자를 찾을 수 없어요.');});
 it('언팔로우 변경이 목록에 바로 반영된다',()=>{const profiles=applyProfileAction(freshProfileState(),'demo-user',{type:'follow',targetId:'yuna'},now);expect(page('/users/demo-user/follows?q=유나','demo-user',undefined,profiles)).toContain('표시할 개발자가 없습니다.');});
 it('사용자별 활동/모집 알림 설정을 표시한다',()=>{const profiles=applyProfileAction(freshProfileState(),'demo-user',{type:'follow-preferences',targetId:'yuna',preferences:{activity:true,projects:false}},now);const html=page('/settings','demo-user',undefined,profiles);expect(html).toContain('새 게시글 활동 알림');expect(html).toContain('새 프로젝트 모집 알림');expect((html.match(/checked=""/g)||[])).toHaveLength(1);});
 it('선택한 새 글 알림이 해당 계정 알림에 반영된다',()=>{let p=applyProfileAction(freshProfileState(),'demo-user',{type:'follow-preferences',targetId:'yuna',preferences:{activity:true,projects:false}},now);p=applyProfileAction(p,'yuna',{type:'social-create',id:'follow-news',input:{...structuredClone(emptyPost),title:'알림 시연 글',body:'새 소식을 함께 공유합니다.'}},now);expect(page('/notifications','demo-user',undefined,p)).toContain('알림 시연 글');expect(page('/notifications','demo-premium',undefined,p)).not.toContain('알림 시연 글');});
 it('메인의 기존 사용량은 보존하고 AI 초안 화면은 미구현 한도를 표시하지 않는다',()=>{const home=matched();expect(page('/','demo-user',home)).toContain('/ 5회');const html=page('/ai/matching','demo-user',home);expect(html).toContain('추천 실행은 준비 중입니다.');expect(html).not.toContain('/ 5회');});
 it('이전 Mock 결과의 무료 상세 이유 잠금과 프리미엄 조회는 보존한다',()=>{expect(page('/ai/matching','demo-user',matched())).toContain('상세 추천 이유는 프리미엄에서');const html=page('/ai/matching','demo-premium',matched(demoAccounts[1]));expect(html).toContain('일치 기술');expect(html).not.toContain('상세 추천 이유는 프리미엄에서');});
 it('이전 사용량이 가득 차도 로컬 메시지와 조건 입력을 제공한다',()=>{const home=matched();home.matching.usage['demo-user']['2026-10']=15;const html=page('/ai/matching','demo-user',home);expect(html).toContain('메시지와 조건을 기록해도 추천 횟수는 차감되지 않습니다.');expect(html).not.toContain('이번 달 추천 횟수를 모두 사용했습니다.');});
 it('다른 계정의 결과는 표시하지 않는다',()=>{const home=matched();const html=page('/ai/matching','demo-premium',home);expect(html).not.toContain('이런 동료와 함께해 보세요');});
 it('비로그인에게 로그인 안내만 표시하고 대화·조건 폼을 숨긴다',()=>{const html=page('/ai/matching');expect(html).toContain('AI 추천 대화는 로그인 후 이용할 수 있습니다.');expect(html).toContain('/login?next=%2Fai%2Fmatching');expect(html).not.toContain('모집 개발 직군 · 복수 선택');expect(html).not.toContain('AI에게 메시지');});
 it('비로그인 메인에 로그인 안내와 로그인으로 연결되는 이전 대화 링크를 표시한다',()=>{const html=page('/');expect(html).toContain('로그인 전 입력은 저장되지 않습니다.');expect(html).toContain('/login?next=%2Fai%2Fmatching');expect(html).toContain('추천 시작');});
});
