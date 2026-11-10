import { resetLocalStores } from "@/lib/localStore";
import { afterEach,beforeEach,describe,expect,it,vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter,Route,Routes } from '@/tests/helpers/RouterFixture';
import { AppLayout } from '@/app/_components/AppLayout';
import FeedPage from '@/app/feeds/page';
import PostDetailPage from '@/app/feeds/[postId]/page';
import { PostEditorPage } from '@/app/feeds/_components/PostEditor';
import { PostForm } from '@/app/feeds/_components/PostForm';
import { FeedPostCard } from '@/app/feeds/_components/FeedPostCard';
import { Comments } from '@/app/feeds/[postId]/_components/Comments';
import ProfilePage from '@/app/(user)/users/[userId]/_components/PrototypeProfilePage';
import { AccountLayout } from '@/app/(user)/_components/AccountLayout';
import HomePage from '@/app/page';
import { signIn } from '@/tests/helpers/session';
import { apiPost } from '@/tests/helpers/feedApiFixture';
import { parseFeedPost } from '@/lib/social/feedApi';
import { applyProfileAction,freshProfileState,PROFILE_KEY } from '@/lib/profiles/profileService';
import { emptyPost } from '@/mocks/social';
import type { ProfileState } from '@/types/profile';
import type { ReactNode } from 'react';
const now=new Date('2026-10-30T14:00:00+09:00');
function created(){return applyProfileAction(freshProfileState(),'demo-user',{type:'social-create',id:'new-story',input:{...structuredClone(emptyPost),title:'이번 주 협업 회고',body:'팀과 함께 성장한 이야기를 기록합니다.',tags:['React'],github:'https://github.com',external:'https://example.com',images:[{id:'img',name:'demo.png',type:'image/png',size:68,url:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aQYQAAAAASUVORK5CYII='}]}},now);}
function page(url:string,userId?:string,state:ProfileState=freshProfileState()){const data=new Map<string,string>();data.set(PROFILE_KEY,JSON.stringify(state));vi.stubGlobal('localStorage',{getItem:(key:string)=>data.get(key)||null});vi.stubGlobal('sessionStorage',{getItem:()=>null});return resetLocalStores(),signIn(userId),renderToStaticMarkup(<><MemoryRouter initialEntries={[url]}><Routes><Route element={<AppLayout/>}><Route path="/feeds" element={<FeedPage/>}/><Route path="/feeds/new" element={<PostEditorPage/>}/><Route path="/feeds/:postId/edit" element={<PostEditorPage/>}/><Route path="/feeds/:postId" element={<PostDetailPage/>}/><Route element={<AccountLayout>{null}</AccountLayout>}><Route path="/users/:userId" element={<ProfilePage/>}/></Route><Route path="/" element={<HomePage/>}/></Route></Routes></MemoryRouter></>);}
// 피드 API 화면의 한 부분을 로그인 상태와 함께 그린다. 데이터는 API 응답 예시(feedApiFixture)다.
function render(node:ReactNode,userId?:string){resetLocalStores();signIn(userId);return renderToStaticMarkup(<MemoryRouter initialEntries={['/feeds']}>{node}</MemoryRouter>);}
const post=(extra:Record<string,unknown>={})=>parseFeedPost(apiPost(extra));
const noop=()=>{};
beforeEach(()=>{vi.useFakeTimers();vi.setSystemTime(now);});afterEach(()=>{vi.unstubAllGlobals();vi.useRealTimers();});

describe('SNS 피드 API 화면',()=>{
 it('피드는 세로 리스트와 모드·정렬·카테고리를 표시하고 목록을 불러온다',()=>{const html=page('/feeds','demo-user');expect(html).toContain('sns-vertical-feed');for(const label of ['추천','팔로잉','추천순','인기순','조회순','최신순','정보','피드백','홍보','자유'])expect(html).toContain(label);expect(html).toContain('/feeds/new');expect(html).toContain('이야기를 불러오는 중이에요.');expect(html).toContain('href="/profile/me?tab=posts"');expect(html).not.toContain('Mock 추천 순서');});
 it('비로그인 팔로잉 탭은 요청하지 않고 로그인을 안내한다',()=>{const html=page('/feeds?mode=following');expect(html).toContain('로그인하고 팔로잉 피드를 확인하세요.');expect(html).not.toContain('이야기를 불러오는 중이에요.');expect(html).toContain('href="/login"');});
 it('게시글 카드는 API 응답의 제목·카테고리·태그·이미지·수와 작성자 프로필 주소를 보여 준다',()=>{const html=render(<FeedPostCard post={post()} onChange={noop}/>,'demo-premium');for(const text of ['제목','본문','정보','#React','교육','조회 3','댓글 1개','프론트엔드 개발자','퍼가기</button>','외부 공유</button>','>신고<'])expect(html).toContain(text);expect(html).toContain('src="/images/feed_7_20261110101500_1.png"');expect(html).toContain('href="/users/member"');expect(html).toContain('href="/feeds/7"');expect(html).not.toContain('/feeds/7/edit');expect(html).not.toContain('숨김');});
 it('요청자 기준 값으로 좋아요·북마크·신고·퍼가기 상태를 표시한다',()=>{const html=render(<FeedPostCard post={post({liked:true,bookmarked:true,reported:true,myRepost:{id:9,content:'의견'}})} onChange={noop}/>,'demo-premium');expect(html).toContain('aria-label="좋아요 취소"');expect(html).toContain('aria-label="북마크 취소"');expect(html).toContain('신고됨');expect(html).toContain('퍼가기 관리');});
 it('내 글에는 수정·삭제 버튼과 숨김 상태를 표시하고 신고 버튼은 없다',()=>{const html=render(<FeedPostCard post={post({mine:true,visibility:'HIDDEN',edited:true})} full onChange={noop}/>,'demo-user');expect(html).toContain('href="/feeds/7/edit"');expect(html).toContain('aria-label="삭제"');expect(html).toContain('정보 · 숨김');expect(html).toContain('수정됨');expect(html).toContain('<h1>제목</h1>');expect(html).not.toContain('>신고<');});
 it('비로그인은 반응 버튼 대신 로그인 링크를 본다',()=>{const html=render(<FeedPostCard post={post()} onChange={noop}/>);expect(html).toContain('aria-label="로그인하고 좋아요"');expect(html).not.toContain('aria-label="좋아요"');expect(html.match(/href="\/login"/g)?.length).toBeGreaterThanOrEqual(3);});
 it('태그가 없는 작성자는 프로필 링크 없이 표시한다',()=>{const html=render(<FeedPostCard post={post({author:{id:2,nickname:null,tag:null,profileImage:null,jobCategories:[]}})} onChange={noop}/>);expect(html).toContain('작성자');expect(html).not.toContain('href="/users/');});
 it('퍼간 글은 원본으로 가는 링크를, 원본을 볼 수 없으면 삭제 안내를 표시한다',()=>{const author=apiPost().author;const available=render(<FeedPostCard post={post({type:'REPOST',title:null,content:'좋은 글',imageUrls:[],original:{id:3,available:true,category:'PROMO',title:'원본 제목',author}})} full onChange={noop}/>,'demo-user');expect(available).toContain('퍼간 글');expect(available).toContain('href="/feeds/3"');expect(available).toContain('원본 제목');expect(available).toContain('좋은 글');expect(available).not.toContain('퍼가기</button>');const gone=render(<FeedPostCard post={post({type:'REPOST',title:null,imageUrls:[],original:{id:3,available:false}})} full onChange={noop}/>,'demo-user');expect(gone).toContain('원본 글이 삭제되었거나 볼 수 없는 상태입니다.');expect(gone).not.toContain('href="/feeds/3"');});
 it('상세 화면은 게시글을 불러오는 동안 안내를 보이고, 잘못된 주소는 찾을 수 없다고 알린다',()=>{expect(page('/feeds/7','demo-user')).toContain('게시글을 불러오는 중이에요.');expect(page('/feeds/react-patterns','demo-user')).toContain('게시글을 찾을 수 없어요.');});
 it('댓글은 불러오는 동안 안내를 보이고 비로그인에게 로그인 링크를 준다',()=>{const signedOut=render(<Comments postId={7} onCountChange={noop}/>);expect(signedOut).toContain('댓글을 불러오는 중이에요.');expect(signedOut).toContain('로그인하고 댓글을 남겨보세요');const signedIn=render(<Comments postId={7} onCountChange={noop}/>,'demo-user');expect(signedIn).toContain('댓글 등록');});
 it('작성 화면은 로그인이 필요하고 이미지·직접 입력 태그·관심 분야를 제공한다',()=>{expect(page('/feeds/new')).toContain('로그인하고 이야기를 나눠보세요.');const html=page('/feeds/new','demo-user');for(const text of ['게시글 등록','이미지 추가','관심 분야','기술 스택 태그 직접 입력','← 내 포스팅'])expect(html).toContain(text);expect(html).toContain('href="/profile/me?tab=posts"');expect(html).not.toContain('GitHub 링크');expect(html).not.toContain('← 전체 피드');for(const code of ['INFO','FEEDBACK','PROMO','COMMUNITY'])expect(html).toContain(`value="${code}"`);});
 it('수정 화면은 서버 게시글을 불러오고, 폼은 기존 값과 남길 이미지를 보여 준다',()=>{expect(page('/feeds/7/edit','demo-user')).toContain('게시글을 불러오는 중이에요.');const html=render(<PostForm post={post({mine:true})}/>,'demo-user');expect(html).toContain('수정 저장');expect(html).toContain('value="제목"');expect(html).toContain('src="/images/feed_7_20261110101500_1.png"');expect(html).toContain('첨부한 이미지 1 제거');});
});

describe('Mock 프로필·메인 화면',()=>{
 it('새 글과 첨부 이미지가 프로필·메인에 반영된다',()=>{const state=created();for(const url of ['/users/demo-user?tab=posts','/']){const html=page(url,'demo-user',state);expect(html).toContain('이번 주 협업 회고');expect(html).toContain('alt="demo.png"');}expect(page('/','demo-user',state)).toContain('SNS 게시글 가로 슬라이드');});
 it('좋아요·북마크·댓글 변경 상태가 프로필에 반영된다',()=>{let state=created();state=applyProfileAction(state,'demo-premium',{type:'social-like',id:'new-story'},now);state=applyProfileAction(state,'demo-premium',{type:'social-bookmark',id:'new-story'},now);state=applyProfileAction(state,'demo-premium',{type:'social-comment-create',postId:'new-story',body:'팀의 기록이 도움이 되었어요.'},now);const html=page('/users/demo-user?tab=posts','demo-premium',state);expect(html).toContain('aria-label="좋아요 취소"');expect(html).toContain('aria-label="북마크 취소"');expect(html).toContain('댓글 1개');});
 it('의견 퍼가기는 내 프로필에 원본 작성자와 원본 링크를 표시한다',()=>{const state=applyProfileAction(created(),'demo-premium',{type:'repost-add',originalId:'new-story',opinion:'다음 팀 회고에서 이야기해 보고 싶어요.'},now);const html=page('/users/demo-premium?tab=posts','demo-premium',state);expect(html).toContain('원본 작성자 · 김하늘');expect(html).toContain('/feeds/new-story');expect(html).toContain('다음 팀 회고에서 이야기해 보고 싶어요.');expect(html).toContain('퍼가기 취소');});
 it('원본 삭제 후 퍼간 글은 삭제 안내를 표시한다',()=>{let state=applyProfileAction(created(),'demo-premium',{type:'repost-add',originalId:'new-story'},now);state=applyProfileAction(state,'demo-user',{type:'social-delete',id:'new-story'},now);expect(page('/users/demo-premium?tab=posts','demo-premium',state)).toContain('원본 게시글이 삭제되었거나 숨김 처리되어 표시할 수 없습니다.');});
});

describe('프로필 포스팅 숨김 표시',()=>{it('숨긴 글은 본인 포스팅에 남고 타인에게 보이지 않는다',()=>{const state=applyProfileAction(created(),'demo-user',{type:'post-hide',id:'new-story'},now);const own=page('/users/demo-user?tab=posts','demo-user',state);expect(own).toContain('이번 주 협업 회고');expect(own).toContain('aria-label="숨김 해제"');expect(own).toContain('lucide-eye-off');expect(own).not.toContain('숨긴 글 보기');expect(own).toContain('포스팅');expect(page('/users/demo-user?tab=posts','demo-premium',state)).not.toContain('이번 주 협업 회고');});});
