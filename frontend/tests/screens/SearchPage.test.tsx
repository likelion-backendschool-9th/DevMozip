import { resetLocalStores } from "@/lib/localStore";
import { afterEach,describe,expect,it,vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter,Route,Routes } from '@/tests/helpers/RouterFixture';
import { AppLayout } from '@/app/_components/AppLayout';
import SearchPage from '@/app/explore/page';
import { signIn } from '@/tests/helpers/session';
function page(query:string,userId?:string){const data=new Map<string,string>();vi.stubGlobal('localStorage',{getItem:(key:string)=>data.get(key)||null});vi.stubGlobal('sessionStorage',{getItem:()=>null});return resetLocalStores(),signIn(userId),renderToStaticMarkup(<><MemoryRouter initialEntries={[`/explore?${query}`]}><Routes><Route element={<AppLayout/>}><Route path="/explore" element={<SearchPage/>}/></Route></Routes></MemoryRouter></>);}
afterEach(()=>vi.unstubAllGlobals());
describe('개발자 탐색 화면 렌더링',()=>{
 it('검색 대상 선택을 없애고 이전 프로젝트 URL에서도 개발자만 검색한다',()=>{const html=page('type=projects&q=유나&role=백엔드&status=모집 중');expect((html.match(/value="유나"/g)||[])).toHaveLength(1);expect(html).not.toContain('검색 대상');expect(html).not.toContain('value="project"');expect(html).not.toContain('개의 프로젝트');expect(html).not.toContain('모집 상태');expect(html).toContain('개발자 검색어');expect(html).toContain('/users/yuna');expect(html).toContain('명의 개발자');expect(html).not.toContain('search-project-list');});
 it('개발자 네임카드는 프로필과 팔로우를 표시하고 협업 제안은 제공하지 않는다',()=>{const html=page('type=developers&q=유나','demo-user');expect(html).toContain('/users/yuna');expect(html).toContain('언팔로우');expect(html).not.toContain('협업 제안');expect(html).toContain('이유나');});
 it('필터 선택과 제거 버튼이 표시된다',()=>{const html=page('type=developers&stack=React&available=yes');expect(html).toContain('React 필터 제거');expect(html).toContain('협업 가능 필터 제거');expect(html).toContain('value="yes" selected=""');});
 it('비로그인 팔로잉 검색은 로그인 안내를 표시한다',()=>{const html=page('type=developers&tab=following');expect(html).toContain('로그인하고 동료들의 소식을 만나보세요.');expect(html).toContain('0 명의 개발자');});
 it('본인 카드에서는 팔로우 대신 내 프로필 링크를 제공한다',()=>{expect(page('type=developers&q=김하늘','demo-user')).toContain('내 프로필 보기');});
 it('검색 결과가 없으면 전체 초기화 동작을 제공한다',()=>{const html=page('type=projects&q=없는프로젝트');expect(html).toContain('조건에 맞는 결과가 없어요.');expect(html).toContain('검색 조건 전체 초기화');});
});
