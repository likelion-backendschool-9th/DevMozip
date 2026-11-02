import { afterEach,beforeEach,describe,expect,it,vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter,Route,Routes } from '@/tests/helpers/RouterFixture';
import { PrototypeProvider } from '@/lib/state/PrototypeProvider';
import { AuthProvider } from '@/lib/state/AuthProvider';
import { ProfileProvider } from '@/lib/state/ProfileProvider';
import { ProjectProvider } from '@/lib/state/ProjectProvider';
import { MessagingProvider } from '@/lib/state/MessagingProvider';
import { HomeProvider } from '@/lib/state/HomeProvider';
import { AppLayout } from '@/components/layout/AppLayout';
import { FeedPage } from '@/app/feed/_components/FeedScreen';
import { PostDetailPage } from '@/app/posts/[postId]/_components/PostDetailScreen';
import { PostEditorPage } from '@/components/posts/editor/PostEditorScreen';
import { ProfilePage } from '@/app/users/[userId]/_components/ProfileScreen';
import { HomePage } from '@/app/_components/HomeScreen';
import { SESSION_KEY } from '@/lib/account/authService';
import { applyProfileAction,freshProfileState,PROFILE_KEY } from '@/lib/profiles/profileService';
import { emptyPost } from '@/mocks/social';
import type { ProfileState } from '@/types/profile';
const now=new Date('2026-10-30T14:00:00+09:00');
function created(){return applyProfileAction(freshProfileState(),'demo-user',{type:'social-create',id:'new-story',input:{...structuredClone(emptyPost),title:'이번 주 협업 회고',body:'팀과 함께 성장한 이야기를 기록합니다.',tags:['React'],github:'https://github.com',external:'https://example.com',images:[{id:'img',name:'demo.png',type:'image/png',size:68,url:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aQYQAAAAASUVORK5CYII='}]}},now);}
function page(url:string,userId?:string,state:ProfileState=freshProfileState()){const data=new Map<string,string>();data.set(PROFILE_KEY,JSON.stringify(state));if(userId)data.set(SESSION_KEY,JSON.stringify({version:1,userId}));vi.stubGlobal('localStorage',{getItem:(key:string)=>data.get(key)||null});vi.stubGlobal('sessionStorage',{getItem:()=>null});return renderToStaticMarkup(<PrototypeProvider><AuthProvider><ProfileProvider><ProjectProvider><MessagingProvider><HomeProvider><MemoryRouter initialEntries={[url]}><Routes><Route element={<AppLayout/>}><Route path="/feed" element={<FeedPage/>}/><Route path="/posts/new" element={<PostEditorPage/>}/><Route path="/posts/:postId/edit" element={<PostEditorPage/>}/><Route path="/posts/:postId" element={<PostDetailPage/>}/><Route path="/users/:userId" element={<ProfilePage/>}/><Route path="/" element={<HomePage/>}/></Route></Routes></MemoryRouter></HomeProvider></MessagingProvider></ProjectProvider></ProfileProvider></AuthProvider></PrototypeProvider>);}
beforeEach(()=>{vi.useFakeTimers();vi.setSystemTime(now);});afterEach(()=>{vi.unstubAllGlobals();vi.useRealTimers();});
describe('SNS 화면 통합 렌더링',()=>{
 it('전체 피드는 세로 리스트와 모드·정렬·카테고리를 표시한다',()=>{const html=page('/feed','demo-user');expect(html).toContain('sns-vertical-feed');for(const label of ['추천','팔로잉','인기순','조회순','최신순','정보','피드백','홍보','자유'])expect(html).toContain(label);expect(html).toContain('/posts/new');expect(html).toContain('/posts/react-patterns');});
 it('새 글과 첨부 이미지가 피드·상세·프로필·메인에 반영된다',()=>{const state=created();for(const url of ['/feed?sort=latest','/posts/new-story','/users/demo-user?tab=posts','/']){const html=page(url,'demo-user',state);expect(html).toContain('이번 주 협업 회고');expect(html).toContain('alt="demo.png"');}expect(page('/','demo-user',state)).toContain('SNS 게시글 가로 슬라이드');});
 it('상세에서 이미지·GitHub/외부 링크·댓글 입력을 표시한다',()=>{const html=page('/posts/new-story','demo-user',created());expect(html).toContain('https://github.com/');expect(html).toContain('https://example.com/');expect(html).toContain('alt="demo.png"');expect(html).toContain('댓글 등록');expect(html).toContain('/posts/new-story/edit');});
 it('좋아요·북마크·댓글 변경 상태가 상세와 프로필에서 일치한다',()=>{let state=created();state=applyProfileAction(state,'demo-premium',{type:'social-like',id:'new-story'},now);state=applyProfileAction(state,'demo-premium',{type:'social-bookmark',id:'new-story'},now);state=applyProfileAction(state,'demo-premium',{type:'social-comment-create',postId:'new-story',body:'팀의 기록이 도움이 되었어요.'},now);for(const url of ['/posts/new-story','/users/demo-user?tab=posts']){const html=page(url,'demo-premium',state);expect(html).toContain('aria-label="좋아요 취소"');expect(html).toContain('aria-label="북마크 취소"');expect(html).toContain('댓글 1개');}expect(page('/posts/new-story','demo-premium',state)).toContain('팀의 기록이 도움이 되었어요.');});
 it('의견 퍼가기는 내 프로필에 원본 작성자와 원본 링크를 표시한다',()=>{const state=applyProfileAction(created(),'demo-premium',{type:'repost-add',originalId:'new-story',opinion:'다음 팀 회고에서 이야기해 보고 싶어요.'},now);const html=page('/users/demo-premium?tab=posts','demo-premium',state);expect(html).toContain('원본 작성자 · 김하늘');expect(html).toContain('/posts/new-story');expect(html).toContain('다음 팀 회고에서 이야기해 보고 싶어요.');expect(html).toContain('퍼가기 취소');});
 it('원본 삭제 후 퍼간 글은 삭제 안내를 표시한다',()=>{let state=applyProfileAction(created(),'demo-premium',{type:'repost-add',originalId:'new-story'},now);state=applyProfileAction(state,'demo-user',{type:'social-delete',id:'new-story'},now);expect(page('/users/demo-premium?tab=posts','demo-premium',state)).toContain('원본 게시글이 삭제되었거나 숨김 처리되어 표시할 수 없습니다.');expect(page('/posts/new-story','demo-premium',state)).toContain('게시글을 찾을 수 없어요.');});
 it('편집은 본인만 가능하고 작성 폼에 이미지·링크·태그를 제공한다',()=>{expect(page('/posts/new-story/edit','demo-premium',created())).toContain('본인 게시글만 수정할 수 있습니다.');const html=page('/posts/new-story/edit','demo-user',created());for(const text of ['수정 저장','이미지 첨부','GitHub 링크','외부 링크','기술 스택 태그'])expect(html).toContain(text);expect(page('/posts/new')).toContain('로그인하고 이야기를 나눠보세요.');});
 it('비로그인 팔로잉/빈 결과 및 숨긴 글에 안내를 표시한다',()=>{expect(page('/feed?mode=following')).toContain('로그인하고 팔로잉 피드를 확인하세요.');expect(page('/feed?q=없는이야기','demo-user')).toContain('조건에 맞는 이야기가 없습니다.');const state=applyProfileAction(created(),'demo-user',{type:'post-hide',id:'new-story'},now);expect(page('/posts/new-story','demo-premium',state)).toContain('게시글을 찾을 수 없어요.');});
 it('퍼가기와 외부 공유는 별도 버튼이다',()=>{const html=page('/posts/new-story','demo-user',created());expect(html).toContain('퍼가기</button>');expect(html).toContain('외부 공유</button>');});
});
