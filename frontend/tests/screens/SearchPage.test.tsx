import { resetLocalStores } from "@/lib/localStore";
import { afterEach,describe,expect,it,vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter,Route,Routes } from '@/tests/helpers/RouterFixture';
import { AppLayout } from '@/app/_components/AppLayout';
import SearchPage from '@/app/explore/page';
import { signIn } from '@/tests/helpers/session';
function page(query:string,userId?:string){const data=new Map<string,string>();vi.stubGlobal('localStorage',{getItem:(key:string)=>data.get(key)||null});vi.stubGlobal('sessionStorage',{getItem:()=>null});return resetLocalStores(),signIn(userId),renderToStaticMarkup(<><MemoryRouter initialEntries={[`/explore?${query}`]}><Routes><Route element={<AppLayout/>}><Route path="/explore" element={<SearchPage/>}/></Route></Routes></MemoryRouter></>);}
afterEach(()=>vi.unstubAllGlobals());
describe('통합 검색 화면 렌더링',()=>{
 it('헤더는 개발자 탐색 메뉴를 유지하고 중복 검색 아이콘 없이 페이지 검색 조건을 반영한다',()=>{const html=page('type=projects&q=Flowmate');expect((html.match(/value="Flowmate"/g)||[])).toHaveLength(1);expect((html.match(/value="project" selected=""/g)||[])).toHaveLength(1);expect(html).not.toContain('aria-label="통합 검색"');expect(html).toContain('개발자 탐색');expect(html).not.toContain('헤더 통합 검색');expect(html).toContain('/projects/flowmate');expect(html).toContain('개의 프로젝트');});
 it('개발자 네임카드는 프로필과 팔로우를 표시하고 협업 제안은 제공하지 않는다',()=>{const html=page('type=developers&q=유나','demo-user');expect(html).toContain('/users/yuna');expect(html).toContain('언팔로우');expect(html).not.toContain('협업 제안');expect(html).toContain('이유나');});
 it('필터 선택과 제거 버튼이 표시된다',()=>{const html=page('type=developers&stack=React&available=yes');expect(html).toContain('React 필터 제거');expect(html).toContain('협업 가능 필터 제거');expect(html).toContain('value="yes" selected=""');});
 it('비로그인 팔로잉 검색은 로그인 안내를 표시한다',()=>{const html=page('type=developers&tab=following');expect(html).toContain('로그인하고 동료들의 소식을 만나보세요.');expect(html).toContain('0 명의 개발자');});
 it('본인 카드에서는 팔로우 대신 내 프로필 링크를 제공한다',()=>{expect(page('type=developers&q=김하늘','demo-user')).toContain('내 프로필 보기');});
 it('검색 결과가 없으면 전체 초기화 동작을 제공한다',()=>{const html=page('type=projects&q=없는프로젝트');expect(html).toContain('조건에 맞는 결과가 없어요.');expect(html).toContain('검색 조건 전체 초기화');});
});
