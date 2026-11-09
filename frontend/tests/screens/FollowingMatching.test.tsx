import { resetLocalStores } from "@/lib/localStore";
import { toAiConditions } from '@/lib/matching/aiApi';
import { buildChatResult,newMatchingConversation,sendMatchingMessage } from '@/lib/matching/matchingChatService';
import { afterEach,beforeEach,describe,expect,it,vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter,Route,Routes } from '@/tests/helpers/RouterFixture';
import { AppLayout } from '@/app/_components/AppLayout';
import FollowingPage from '@/app/(user)/users/[userId]/follows/page';
import { AccountLayout } from '@/app/(user)/_components/AccountLayout';
import { SavedRecommendations } from '@/app/ai/matching/_components/SavedRecommendations';
import { ChatComposer } from '@/app/ai/matching/_components/ChatComposer';
import { RecommendationCards } from '@/app/ai/matching/_components/RecommendationCards';
import { PublicProjectDetail } from '@/app/projects/[projectId]/_components/PublicProjectDetail';
import type { AiRecommendation } from '@/types/aiRecommendation';
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
 it('실제 프로젝트 추천은 모달 대신 상세 페이지 링크를 제공한다',()=>{
  const html=renderToStaticMarkup(<RecommendationCards developers={[]} tab="projects" projects={[{id:8,recommendedProjectId:42,recommendedUserId:null,matchScore:80,basicReason:'추천 이유',rank:1,createdAt:now.toISOString()}]}/>);
  expect(html).toContain('href="/projects/42"');expect(html).toContain('프로젝트 상세 보기');expect(html).not.toContain('role="dialog"');
  const detail=renderToStaticMarkup(<PublicProjectDetail projectId={42}/>);
  expect(detail).toContain('프로젝트 정보를 불러오는 중');expect(detail).not.toContain('Mock 공고');
 });
 it('팔로잉 검색에 프로필·협업 상태·알림 설정을 표시한다',()=>{const html=page('/users/demo-user/follows?q=유나','demo-user');expect(html).toContain('이유나');expect(html).not.toContain('박민준');expect(html).toContain('/users/yuna');expect(html).toContain('협업 가능');expect(html).toContain('언팔로우');expect(html).toContain('이유나 알림 설정');});
 it('팔로워 탭과 공개 사용자 목록을 조회할 수 있다',()=>{const html=page('/users/yuna/follows?tab=followers','demo-user');expect(html).toContain('김하늘');expect(html).toContain('팔로워');expect(page('/users/missing/follows','demo-user')).toContain('사용자를 찾을 수 없어요.');});
 it('언팔로우 변경이 목록에 바로 반영된다',()=>{const profiles=applyProfileAction(freshProfileState(),'demo-user',{type:'follow',targetId:'yuna'},now);expect(page('/users/demo-user/follows?q=유나','demo-user',undefined,profiles)).toContain('표시할 개발자가 없습니다.');});
 it('사용자별 활동/모집 알림 설정을 표시한다',()=>{const profiles=applyProfileAction(freshProfileState(),'demo-user',{type:'follow-preferences',targetId:'yuna',preferences:{activity:true,projects:false}},now);const html=page('/settings','demo-user',undefined,profiles);expect(html).toContain('새 게시글 활동 알림');expect(html).toContain('새 프로젝트 모집 알림');expect((html.match(/checked=""/g)||[])).toHaveLength(1);});
 it('선택한 새 글 알림이 해당 계정 알림에 반영된다',()=>{let p=applyProfileAction(freshProfileState(),'demo-user',{type:'follow-preferences',targetId:'yuna',preferences:{activity:true,projects:false}},now);p=applyProfileAction(p,'yuna',{type:'social-create',id:'follow-news',input:{...structuredClone(emptyPost),title:'알림 시연 글',body:'새 소식을 함께 공유합니다.'}},now);expect(page('/notifications','demo-user',undefined,p)).toContain('알림 시연 글');expect(page('/notifications','demo-premium',undefined,p)).not.toContain('알림 시연 글');});
 it('메인의 기존 사용량은 보존하고 AI 초안 화면은 미구현 한도를 표시하지 않는다',()=>{const home=matched();expect(page('/','demo-user',home)).toContain('/ 5회');const html=page('/ai/matching','demo-user',home);expect(html).toContain('서버 사용량 집계 준비 중');expect(html).not.toContain('2/5');});
 it('분야를 관심 분야 선택지로 통합하고 필수 조건 세 개만 안내한다',()=>{const html=page('/ai/matching','demo-user');expect(html).toContain('3개 항목 확인 필요');expect(html).toContain('관심 분야');expect((html.match(/>\(필수\)<\/span>/g)||[])).toHaveLength(3);expect(html).toContain('기술 스택 직접 입력');expect(html).toContain('기술 추가');for(const label of ['생산성','핀테크','교육','커뮤니티','AI','웹','모바일','AI / 머신러닝','데이터','게임','보안','클라우드 / 인프라'])expect(html).toContain(label);expect(html).not.toContain('프로젝트 분야');expect(html).not.toContain('기타 분야 직접 입력');expect(html).toContain('기타 직군 직접 입력');});
 it('분야·경력·기간만 선택해도 조건 준비 완료로 표시한다',()=>{const home=matched();home.chat.conversations['demo-user'][0].requirements={description:'',fields:[],interests:['웹'],stacks:[],roles:[],career:'경력 무관',duration:'3개월'};const html=page('/ai/matching','demo-user',home);expect(html).toContain('조건 준비 완료');expect(html).not.toContain('개 항목 확인 필요');});
 it('이전 Mock 결과의 무료 상세 이유 잠금과 프리미엄 조회는 보존한다',()=>{expect(page('/ai/matching','demo-user',matched())).toContain('상세 추천 이유는 프리미엄에서');const html=page('/ai/matching','demo-premium',matched(demoAccounts[1]));expect(html).toContain('일치 기술');expect(html).not.toContain('상세 추천 이유는 프리미엄에서');});
 it('이전 사용량이 가득 차도 로컬 메시지와 조건 입력을 제공한다',()=>{const home=matched();home.matching.usage['demo-user']['2026-10']=15;const html=page('/ai/matching','demo-user',home);expect(html).toContain('대화·조건 변경은 추천 횟수에 영향을 주지 않아요.');expect(html).not.toContain('이번 달 추천 횟수를 모두 사용했습니다.');});
 it('다른 계정의 결과는 표시하지 않는다',()=>{const home=matched();const html=page('/ai/matching','demo-premium',home);expect(html).not.toContain('이런 동료와 함께해 보세요');});
 it('같은 성공 조건에서는 두 추천 버튼이 비활성화되고 조건 변경 시 다시 활성화된다',()=>{
  const state=matched();const c=state.chat.conversations['demo-user'][0];c.requirements.fields=[];
  c.lastRecommendation={requestId:91,conditions:toAiConditions(c.requirements)};
  const html=page('/ai/matching','demo-user',state);
  const buttons=(html.match(/<button\b[^>]*>[\s\S]*?<\/button>/g)||[]).filter(v=>v.includes('조건으로 추천 받기'));
  expect(buttons).toHaveLength(2);expect(buttons.every(v=>v.split('>')[0].includes('disabled=""'))).toBe(true);
  expect(html).toContain('추천 조건을 변경하면 다시 추천받을 수 있어요.');
  c.requirements.duration='6개월';
  const changed=page('/ai/matching','demo-user',state);
  expect(changed).not.toContain('추천 조건을 변경하면 다시 추천받을 수 있어요.');
 });
 it('실제 닉네임·공개 정보와 결과 뒤 새 메시지를 표시하고 이력 선택창은 제거한다',()=>{
  page('/ai/matching','demo-user');
  const result:AiRecommendation={requestId:10,conversationId:7,messageId:8,status:'SUCCESS',createdAt:'2026-11-08T18:01:00',completedAt:'2026-11-08T18:01:01',conditions:{interests:['보안'],stacks:['Java','React'],career:'경력 무관',durationMonths:3},projects:[],developers:[{id:11,recommendedUserId:3,recommendedProjectId:null,matchScore:80,basicReason:'저장된 실제 추천 이유\n요청 기술 2개 중 1개가 일치합니다.',rank:1,createdAt:'2026-11-08T18:01:00',developer:{tag:'real-user',nickname:'실제 회원 닉네임',profileImage:'/api/v1/profile-images/avatar.png',jobCategories:['백엔드 개발자'],skills:['Java'],career:'3년 차',collaborationStatus:'AVAILABLE',trustScore:37.5}}]};
  const html=renderToStaticMarkup(<SavedRecommendations serverId={7} result={result} messages={[{id:'first',serverId:8,role:'user',text:'첫 요청',createdAt:'2026-11-08T18:00:00'},{id:'next',role:'user',text:'결과 다음에 보낸 메시지',createdAt:'2026-11-08T09:02:00Z'}]}/>);
  expect(html.indexOf('첫 요청')).toBeLessThan(html.indexOf('저장된 실제 추천 이유'));
  expect(html.indexOf('저장된 실제 추천 이유')).toBeLessThan(html.indexOf('결과 다음에 보낸 메시지'));
  const evaluated=renderToStaticMarkup(<SavedRecommendations result={{...result,developers:result.developers.map(item=>({...item,baseScore:90,requirementScore:50,requirementsApplied:true,requirementAssessments:[{requirementId:'r0',requirement:'결제 경험',mode:'PREFERENCE',status:'UNKNOWN',source:null,field:null,quote:null}]}))}}/>);
  expect(evaluated).toContain('기본 적합도 90.00점');
  expect(evaluated).toContain('요구사항 충족도 50.00점');
  expect(evaluated).toContain('미확인');
  expect(evaluated).toContain('경험이 없다는 뜻은 아닙니다');
  expect(html).toContain('실제 회원 닉네임');expect(html).toContain('/users/real-user');expect(html).toContain('백엔드 개발자');expect(html).toContain('Java');expect(html).toContain('/api/v1/profile-images/avatar.png');
  expect(html).toContain('제시하신 조건 중 Java 기술 스택이 현재 프로필에 등록되어 있습니다.');expect(html).not.toContain('Java, React 기술 스택이');expect(html).toContain('신뢰 온도 37.5도');expect(html).toContain('37.5°C');expect(html).toContain('현재 협업 상태: 협업 가능');expect(html).toContain('3년 차');expect(html).toContain('data-status="AVAILABLE"');expect(html).toContain('<dt>직군</dt>');expect(html).toContain('<dt>경력</dt>');
  const changed=renderToStaticMarkup(<SavedRecommendations serverId={7} result={{...result,developers:result.developers.map(item=>({...item,developer:{...item.developer!,career:null,collaborationStatus:'BUSY'}}))}}/>);
  expect(changed).toContain('경력 미입력');expect(changed).toContain('현재 협업 어려움');expect(changed).not.toContain('data-status="AVAILABLE"');
  const unknown=renderToStaticMarkup(<SavedRecommendations serverId={7} result={{...result,developers:result.developers.map(item=>({...item,developer:{...item.developer!,collaborationStatus:null,trustScore:null}}))}}/>);expect(unknown).toContain('협업 상태 미등록');expect(unknown).toContain('온도 미등록');expect(unknown).not.toContain('36.5°C');
  expect(html).not.toContain('개발자 #3');expect(html).not.toContain('추천 이력');expect(html).not.toContain('정보 미연결');
 });
 it('누적 한도 초과 입력은 전송 버튼을 비활성화하고 남은 용량을 표시한다',()=>{
  const html=renderToStaticMarkup(<ChatComposer value="추가" used={10000} signedIn busy={false} onChange={()=>{}} onSend={()=>{}}/>);
  expect(html).toContain('전송 한도를 초과');expect(html).toContain('남은 용량 0자');expect(html).toMatch(/disabled="" aria-label="메시지 전송"/);
 });
 it('비로그인도 입력과 예시를 보고 서버 저장은 로그인 후 실행한다',()=>{const html=page('/ai/matching');expect(html).toContain('로그인 후 추천을 실행할 수 있어요.');expect(html).toContain('AI에게 메시지');expect(html).toContain('새 프로젝트의 동료 찾기');expect(html).not.toContain('추천을 위해 조금만 더 알려주세요');expect(html).toContain('입력은 서버에 저장되지 않으며');});
 it('비로그인 메인에 로그인 안내와 로그인으로 연결되는 이전 대화 링크를 표시한다',()=>{const html=page('/');expect(html).toContain('입력을 잠시 보관하고 로그인 후 대화로 이어갑니다.');expect(html).toContain('/login?next=%2Fai%2Fmatching');expect(html).toContain('추천 시작');});
});
