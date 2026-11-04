import {describe,it,expect} from 'vitest';
import {existsSync,readFileSync} from 'node:fs';
import path from 'node:path';
import {routes} from '@/mocks/routes';
import {routeTitle} from '@/lib/shared/routeService';
describe('Next.js App Router 파일 기반 경로',()=>{
 it('메타데이터의 경로가 중복되지 않는다',()=>expect(new Set(routes.map(r=>r.path)).size).toBe(routes.length));
 it.each(routes)('$path에 실제 app/page.tsx가 존재한다',r=>{const folder=r.path.replace(/:([^/]+)/g,'[$1]');expect(['','(user)'].some(group=>existsSync(path.join(import.meta.dirname,'../app',group,folder,'page.tsx'))),folder).toBe(true);});
 it('도메인 동기화 컴포넌트가 루트 레이아웃에 있다',()=>{const layout=readFileSync('app/layout.tsx','utf8');expect(layout).toContain('<ProjectSync />');expect(layout).toContain('<MessagingSync />');expect(layout).not.toMatch(/<[A-Za-z]*Provider/);});
 it('서버 루트 레이아웃과 404가 있고 이전 라우터 디렉터리는 없다',()=>{expect(existsSync('app/layout.tsx')).toBe(true);expect(existsSync('app/not-found.tsx')).toBe(true);expect(existsSync('src')).toBe(false);expect(readFileSync('package.json','utf8')).not.toContain('react-router-dom');});
 it.each([['/profile/me','내 프로필'],['/users/demo-user','개발자 프로필'],['/projects/pocket','프로젝트 상세'],['/projects/new','모집 공고 작성'],['/projects/pocket/manage','프로젝트 관리'],['/feeds/new','게시글 작성'],['/feeds/haneul-retro/edit','게시글 수정'],['/messages/projects/pocket','프로젝트 채팅'],['/missing','페이지를 찾을 수 없습니다']])('%s 문서 제목을 유지한다',(pathname,title)=>expect(routeTitle(pathname)).toBe(`${title} · DevMozip`));
});
