import { routeTitle } from '../../services/routeService';
import { describe, it, expect } from 'vitest';
import { matchRoutes } from 'react-router-dom';
import { routes } from '../../mocks/routes';
describe('라우팅 구성', () => {
 const config = routes.map(route => ({ path: route.path }));
 it('경로가 중복되지 않는다', () => expect(new Set(routes.map(route => route.path)).size).toBe(routes.length));
 it.each(routes)('$path 경로가 올바른 화면으로 연결된다', route => {
 const path = route.path.replace(':userId', 'demo-user').replace(':projectId', 'demo-project').replace(':postId', 'demo-post');
 expect(matchRoutes(config, path)?.at(-1)?.route.path).toBe(route.path);
 });
 it('잘못된 경로는 정상 경로와 일치하지 않는다', () => expect(matchRoutes(config, '/missing-page')).toBeNull());
});

describe('발표 화면 제목',()=>{it.each([['/users/demo-user','개발자 프로필'],['/projects/pocket','프로젝트 상세'],['/projects/new','모집 공고 작성'],['/projects/pocket/manage','프로젝트 관리'],['/posts/new','게시글 작성'],['/posts/haneul-retro/edit','게시글 수정'],['/chats/pocket','프로젝트 채팅'],['/missing','페이지를 찾을 수 없습니다']])('%s의 실제 제목과 정적 경로 우선순위를 유지한다',(pathname,title)=>expect(routeTitle(pathname)).toBe(`${title} · DevMozip`));});
