import {describe,it,expect} from 'vitest';
import {existsSync,readFileSync} from 'node:fs';
import path from 'node:path';
import {routes} from '@/mocks/routes';
import {routeTitle} from '@/lib/shared/routeService';
describe('Next.js App Router 파일 기반 경로',()=>{
 it('메타데이터의 경로가 중복되지 않는다',()=>expect(new Set(routes.map(r=>r.path)).size).toBe(28));
 it.each(routes)('$path에 실제 app/page.tsx가 존재한다',r=>{const folder=r.path.replace(/:([^/]+)/g,'[$1]');expect(existsSync(path.join(import.meta.dirname,'../app',folder,'page.tsx')),folder).toBe(true);});
 it('서버 루트 레이아웃과 404가 있고 이전 라우터 디렉터리는 없다',()=>{expect(existsSync('app/layout.tsx')).toBe(true);expect(existsSync('app/not-found.tsx')).toBe(true);expect(existsSync('src')).toBe(false);expect(readFileSync('package.json','utf8')).not.toContain('react-router-dom');});
 it.each([['/users/demo-user','개발자 프로필'],['/projects/pocket','프로젝트 상세'],['/projects/new','모집 공고 작성'],['/projects/pocket/manage','프로젝트 관리'],['/posts/new','게시글 작성'],['/posts/haneul-retro/edit','게시글 수정'],['/chats/pocket','프로젝트 채팅'],['/missing','페이지를 찾을 수 없습니다']])('%s 문서 제목을 유지한다',(pathname,title)=>expect(routeTitle(pathname)).toBe(`${title} · DevMozip`));
});
