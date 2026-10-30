import { afterEach,describe,expect,it,vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter,Route,Routes } from 'react-router-dom';
import { PrototypeProvider } from '../app/providers/PrototypeProvider';
import { AuthProvider } from '../app/providers/AuthProvider';
import { ProfileProvider } from '../app/providers/ProfileProvider';
import { ProjectProvider } from '../app/providers/ProjectProvider';
import { MessagingProvider } from '../app/providers/MessagingProvider';
import { HomeProvider } from '../app/providers/HomeProvider';
import { AppLayout } from '../components/layout/AppLayout';
import { SearchPage } from './SearchPage';
import { SESSION_KEY } from '../services/authService';
function page(query:string,userId?:string){const data=new Map<string,string>();if(userId)data.set(SESSION_KEY,JSON.stringify({version:1,userId}));vi.stubGlobal('localStorage',{getItem:(key:string)=>data.get(key)||null});vi.stubGlobal('sessionStorage',{getItem:()=>null});return renderToStaticMarkup(<PrototypeProvider><AuthProvider><ProfileProvider><ProjectProvider><MessagingProvider><HomeProvider><MemoryRouter initialEntries={[`/search?${query}`]}><Routes><Route element={<AppLayout/>}><Route path="/search" element={<SearchPage/>}/></Route></Routes></MemoryRouter></HomeProvider></MessagingProvider></ProjectProvider></ProfileProvider></AuthProvider></PrototypeProvider>);}
afterEach(()=>vi.unstubAllGlobals());
describe('통합 검색 화면 렌더링',()=>{
 it('헤더는 검색 이동 아이콘을 제공하고 페이지 검색창에 URL 조건을 반영한다',()=>{const html=page('target=project&q=Flowmate');expect((html.match(/value="Flowmate"/g)||[])).toHaveLength(1);expect((html.match(/value="project" selected=""/g)||[])).toHaveLength(1);expect(html).toContain('aria-label="통합 검색"');expect(html).not.toContain('헤더 통합 검색');expect(html).toContain('/projects/flowmate');expect(html).toContain('개의 프로젝트');});
 it('개발자 네임카드의 프로필/팔로우/협업 제안이 표시된다',()=>{const html=page('target=developer&q=유나','demo-user');expect(html).toContain('/users/yuna');expect(html).toContain('언팔로우');expect(html).toContain('협업 제안');expect(html).toContain('이유나');});
 it('필터 선택과 제거 버튼이 표시된다',()=>{const html=page('target=developer&stack=React&available=yes');expect(html).toContain('React 필터 제거');expect(html).toContain('협업 가능 필터 제거');expect(html).toContain('value="yes" selected=""');});
 it('비로그인 팔로잉 검색은 로그인 안내를 표시한다',()=>{const html=page('target=developer&scope=following');expect(html).toContain('로그인하고 동료들의 소식을 만나보세요.');expect(html).toContain('0 명의 개발자');});
 it('본인 카드에서는 팔로우 대신 내 프로필 링크를 제공한다',()=>{expect(page('target=developer&q=김하늘','demo-user')).toContain('내 프로필 보기');});
 it('검색 결과가 없으면 전체 초기화 동작을 제공한다',()=>{const html=page('target=project&q=없는프로젝트');expect(html).toContain('조건에 맞는 결과가 없어요.');expect(html).toContain('검색 조건 전체 초기화');});
});
